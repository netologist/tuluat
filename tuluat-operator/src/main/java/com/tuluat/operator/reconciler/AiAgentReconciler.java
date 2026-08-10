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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * JOSDK Reconciler for managing AiAgent Custom Resources and creating
 * Deployment, Service, & Ingress.
 */
@Component
@ControllerConfiguration(name = "aiagent-reconciler")
public class AiAgentReconciler implements Reconciler<AiAgent> {
	private static final Logger log = LoggerFactory.getLogger(AiAgentReconciler.class);

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

			// Calculate active skills using Java Streams
			List<String> activeSkills = spec.skills().stream().filter(s -> Boolean.TRUE.equals(s.enabled()))
					.map(s -> s.name()).toList();

			String effectiveModel = (spec.model() != null && !spec.model().isBlank())
					? spec.model()
					: (provider != null && provider.getSpec() != null) ? provider.getSpec().defaultModel() : "gpt-4o";

			String readyMessage = String.format("AiAgent '%s' successfully reconciled and listening at %s", name,
					ingressUrl);
			agent.setStatus(AiAgentStatus.ready(ingressUrl, activeSkills, effectiveModel, readyMessage,
					agent.getMetadata().getGeneration()));

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
		Map<String, String> labels = Map.of("app", agent.getMetadata().getName(), "component", "ai-agent");

		Deployment deployment = new DeploymentBuilder().withNewMetadata().withName(deployName).withNamespace(ns)
				.withOwnerReferences(ownerRef).withLabels(labels).endMetadata().withNewSpec()
				.withReplicas(agent.getSpec().replicas()).withNewSelector().withMatchLabels(labels).endSelector()
				.withNewTemplate().withNewMetadata().withLabels(labels).endMetadata().withNewSpec()
				.withServiceAccountName("k8s-ai-operator-sa").addNewContainer().withName("ai-agent-app")
				.withImage("k8s-crd-ai-operator:latest").withImagePullPolicy("IfNotPresent").addNewPort()
				.withContainerPort(8080).withName("http").endPort().addNewEnv().withName("AGENT_NAME")
				.withValue(agent.getMetadata().getName()).endEnv().endContainer().endSpec().endTemplate().endSpec()
				.build();

		client.apps().deployments().inNamespace(ns).resource(deployment).serverSideApply();
	}

	private void reconcileService(AiAgent agent, OwnerReference ownerRef, String ns) {
		String svcName = agent.getMetadata().getName() + "-svc";
		Map<String, String> labels = Map.of("app", agent.getMetadata().getName());

		Service service = new ServiceBuilder().withNewMetadata().withName(svcName).withNamespace(ns)
				.withOwnerReferences(ownerRef).withLabels(labels).endMetadata().withNewSpec().withSelector(labels)
				.addNewPort().withName("http").withPort(80).withTargetPort(new IntOrString(8080)).withProtocol("TCP")
				.endPort().withType("ClusterIP").endSpec().build();

		client.services().inNamespace(ns).resource(service).serverSideApply();
	}

	private String reconcileIngress(AiAgent agent, OwnerReference ownerRef, String ns) {
		var ingressSpec = agent.getSpec().ingress();
		if (ingressSpec == null || !Boolean.TRUE.equals(ingressSpec.enabled())) {
			return "http://localhost:8080/api/v1/agents/" + agent.getMetadata().getName() + "/chat";
		}

		String ingressName = agent.getMetadata().getName() + "-ingress";
		String host = (ingressSpec.host() != null && !ingressSpec.host().isBlank())
				? ingressSpec.host()
				: "ai-agent.tuluat.com";
		String path = (ingressSpec.path() != null) ? ingressSpec.path() : "/";
		String svcName = agent.getMetadata().getName() + "-svc";

		var ingressBuilder = new IngressBuilder().withNewMetadata().withName(ingressName).withNamespace(ns)
				.withOwnerReferences(ownerRef).withAnnotations(ingressSpec.annotations()).endMetadata().withNewSpec();

		if (ingressSpec.ingressClassName() != null) {
			ingressBuilder.withIngressClassName(ingressSpec.ingressClassName());
		}

		var pathRule = new HTTPIngressPathBuilder().withPath(path).withPathType(ingressSpec.pathType()).withNewBackend()
				.withNewService().withName(svcName).withNewPort().withNumber(80).endPort().endService().endBackend()
				.build();

		var rule = new IngressRuleBuilder().withHost(host)
				.withHttp(new HTTPIngressRuleValueBuilder().withPaths(pathRule).build()).build();

		ingressBuilder.withRules(rule);

		if (ingressSpec.tls() != null) {
			var tls = new IngressTLSBuilder().withSecretName(ingressSpec.tls().secretName())
					.withHosts(ingressSpec.tls().hosts()).build();
			ingressBuilder.withTls(tls);
		}

		Ingress ingress = ingressBuilder.endSpec().build();
		client.network().v1().ingresses().inNamespace(ns).resource(ingress).serverSideApply();

		String protocol = (ingressSpec.tls() != null) ? "https" : "http";
		return String.format("%s://%s%s", protocol, host,
				path.endsWith("/") ? path.substring(0, path.length() - 1) : path);
	}
}
