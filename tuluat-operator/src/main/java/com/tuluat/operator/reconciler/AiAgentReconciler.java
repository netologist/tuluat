package com.tuluat.operator.reconciler;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.crd.agent.AiAgentStatus;
import com.tuluat.crd.provider.LlmProvider;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressRuleValueBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRuleBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLSBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * JOSDK Reconciler for managing AiAgent Custom Resources and creating
 * Deployment, Service, & Ingress.
 */
@Component
@ControllerConfiguration(name = "aiagent-reconciler")
@Slf4j
public class AiAgentReconciler implements Reconciler<AiAgent> {
	private final KubernetesClient client;

	@Autowired
	public AiAgentReconciler(KubernetesClient client) {
		this.client = client;
	}

	@Override
	public UpdateControl<AiAgent> reconcile(AiAgent agent, Context<AiAgent> context) {
		String name = agent.getMetadata().getName();
		String ns = agent.getMetadata().getNamespace();
		log.info("Reconciling AiAgent CR: {}/{}", ns, name);

		try {
			var spec = agent.getSpec();
			if (spec == null) {
				agent.setStatus(
						AiAgentStatus.failed("AiAgent spec cannot be null", agent.getMetadata().getGeneration()));
				return UpdateControl.patchStatus(agent);
			}

			// Fetch LlmProvider reference if specified
			LlmProvider provider = null;
			if (spec.providerRef() != null && spec.providerRef().name() != null) {
				String pName = spec.providerRef().name();
				String pNs = (spec.providerRef().namespace() != null) ? spec.providerRef().namespace() : ns;
				provider = client.resources(LlmProvider.class).inNamespace(pNs).withName(pName).get();
				if (provider == null) {
					log.warn("Referenced LlmProvider '{}' not found in namespace '{}'", pName, pNs);
					agent.setStatus(AiAgentStatus.reconciling("Waiting for LlmProvider '" + pName + "' to be ready",
							agent.getMetadata().getGeneration()));
					return UpdateControl.patchStatus(agent);
				}
			}

			// Owner reference for garbage collection
			OwnerReference ownerRef = new OwnerReferenceBuilder().withApiVersion(agent.getApiVersion())
					.withKind(agent.getKind()).withName(agent.getMetadata().getName())
					.withUid(agent.getMetadata().getUid()).withBlockOwnerDeletion(true).withController(true).build();

			// Step 1: Reconcile Deployment
			reconcileDeployment(agent, ownerRef, ns);

			// Step 2: Reconcile Service
			reconcileService(agent, ownerRef, ns);

			// Step 3: Reconcile Ingress (Public Exposure)
			String ingressUrl = reconcileIngress(agent, ownerRef, ns);

			// Calculate active skills, tools, and MCP servers using Java Streams
			List<String> activeSkills = spec.skills().stream().filter(s -> Boolean.TRUE.equals(s.enabled()))
					.map(s -> s.name()).toList();
			List<String> activeTools = spec.tools().stream().filter(t -> Boolean.TRUE.equals(t.enabled()))
					.map(t -> t.name()).toList();
			List<String> activeMcpServers = (spec.mcpServers() != null)
					? spec.mcpServers().stream().map(com.tuluat.crd.agent.McpServerRef::name)
							.filter(n -> n != null && !n.isBlank()).toList()
					: List.of();

			String effectiveModel = (spec.model() != null && !spec.model().isBlank())
					? spec.model()
					: (provider != null && provider.getSpec() != null) ? provider.getSpec().defaultModel() : "gpt-4o";

			String readyMessage = String.format("AiAgent '%s' successfully reconciled and listening at %s", name,
					ingressUrl);
			agent.setStatus(AiAgentStatus.ready(ingressUrl, activeSkills, activeTools, activeMcpServers, effectiveModel,
					readyMessage, agent.getMetadata().getGeneration()));

			log.info("Successfully reconciled AiAgent {}/{} -> Status: {}", ns, name, agent.getStatus().phase());
			return UpdateControl.patchStatus(agent);
		} catch (Exception e) {
			log.error("Error reconciling AiAgent {}", name, e);
			agent.setStatus(AiAgentStatus.failed("Reconciliation failure: " + e.getMessage(),
					agent.getMetadata().getGeneration()));
			return UpdateControl.patchStatus(agent);
		}
	}

	private void reconcileDeployment(AiAgent agent, OwnerReference ownerRef, String ns) {
		String deployName = agent.getMetadata().getName() + "-deployment";
		int replicas = (agent.getSpec().replicas() != null) ? agent.getSpec().replicas() : 1;

		Deployment deployment = new DeploymentBuilder().withNewMetadata().withName(deployName).withNamespace(ns)
				.withOwnerReferences(ownerRef).endMetadata().withNewSpec().withReplicas(replicas).withNewSelector()
				.withMatchLabels(Map.of("app", agent.getMetadata().getName())).endSelector().withNewTemplate()
				.withNewMetadata().withLabels(Map.of("app", agent.getMetadata().getName())).endMetadata().withNewSpec()
				.addNewContainer().withName("agent-runtime").withImage("tuluat-operator:latest").addNewEnv()
				.withName("AGENT_NAME").withValue(agent.getMetadata().getName()).endEnv().endContainer().endSpec()
				.endTemplate().endSpec().build();

		client.apps().deployments().inNamespace(ns).resource(deployment).createOrReplace();
		log.info("Deployment {} reconciled for agent {}", deployName, agent.getMetadata().getName());
	}

	private void reconcileService(AiAgent agent, OwnerReference ownerRef, String ns) {
		String svcName = agent.getMetadata().getName() + "-svc";
		Service service = new ServiceBuilder().withNewMetadata().withName(svcName).withNamespace(ns)
				.withOwnerReferences(ownerRef).endMetadata().withNewSpec()
				.withSelector(Map.of("app", agent.getMetadata().getName())).addNewPort().withProtocol("TCP")
				.withPort(8080).withTargetPort(new IntOrString(8080)).endPort().endSpec().build();

		client.services().inNamespace(ns).resource(service).createOrReplace();
		log.info("Service {} reconciled for agent {}", svcName, agent.getMetadata().getName());
	}

	private String reconcileIngress(AiAgent agent, OwnerReference ownerRef, String ns) {
		var spec = agent.getSpec();
		if (spec.ingress() == null || !Boolean.TRUE.equals(spec.ingress().enabled())) {
			return "http://" + agent.getMetadata().getName() + "-svc." + ns + ".svc.cluster.local:8080";
		}

		var ingSpec = spec.ingress();
		String ingName = agent.getMetadata().getName() + "-ingress";
		String host = (ingSpec.host() != null && !ingSpec.host().isBlank())
				? ingSpec.host()
				: agent.getMetadata().getName() + ".tuluat.local";
		String path = (ingSpec.path() != null) ? ingSpec.path() : "/";
		String pathType = (ingSpec.pathType() != null) ? ingSpec.pathType() : "Prefix";
		String svcName = agent.getMetadata().getName() + "-svc";

		var ingRuleValueBuilder = new HTTPIngressRuleValueBuilder().addNewPath().withPath(path).withPathType(pathType)
				.withNewBackend().withNewService().withName(svcName).withNewPort().withNumber(8080).endPort()
				.endService().endBackend().endPath();

		var ingRule = new IngressRuleBuilder().withHost(host).withHttp(ingRuleValueBuilder.build()).build();

		var builder = new IngressBuilder().withNewMetadata().withName(ingName).withNamespace(ns)
				.withOwnerReferences(ownerRef);
		if (ingSpec.annotations() != null && !ingSpec.annotations().isEmpty()) {
			builder.withAnnotations(ingSpec.annotations());
		}
		Ingress meta = builder.endMetadata().build();

		var specBuilder = new IngressSpecBuilderLike(ingRule);
		if (ingSpec.ingressClassName() != null) {
			specBuilder.ingressClassName = ingSpec.ingressClassName();
		}
		if (ingSpec.tls() != null && ingSpec.tls().secretName() != null) {
			specBuilder.tlsSecretName = ingSpec.tls().secretName();
			specBuilder.tlsHosts = ingSpec.tls().hosts();
		}

		Ingress ingress = specBuilder.build(meta);
		client.network().v1().ingresses().inNamespace(ns).resource(ingress).createOrReplace();

		String scheme = (ingSpec.tls() != null && ingSpec.tls().secretName() != null) ? "https" : "http";
		return scheme + "://" + host + path;
	}

	private static final class IngressSpecBuilderLike {
		private final io.fabric8.kubernetes.api.model.networking.v1.IngressRule rule;
		private String ingressClassName;
		private String tlsSecretName;
		private List<String> tlsHosts;

		private IngressSpecBuilderLike(io.fabric8.kubernetes.api.model.networking.v1.IngressRule rule) {
			this.rule = rule;
		}

		private Ingress build(Ingress meta) {
			var builder = new IngressBuilder(meta).withNewSpec().withRules(rule);
			if (ingressClassName != null) {
				builder.withIngressClassName(ingressClassName);
			}
			if (tlsSecretName != null) {
				var tlsBuilder = new IngressTLSBuilder().withSecretName(tlsSecretName);
				if (tlsHosts != null && !tlsHosts.isEmpty()) {
					tlsBuilder.withHosts(tlsHosts);
				}
				builder.withTls(tlsBuilder.build());
			}
			return builder.endSpec().build();
		}
	}
}
