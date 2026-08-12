package com.tuluat.operator.reconciler;

import com.tuluat.crd.agent.AiAgent;
import com.tuluat.crd.agent.AiAgentSpec;
import com.tuluat.crd.agent.IngressSpec;
import com.tuluat.crd.agent.ProviderRef;
import com.tuluat.crd.agent.ToolDefinition;
import com.tuluat.crd.provider.LlmProvider;
import com.tuluat.crd.provider.LlmProviderSpec;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.AppsAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NetworkAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiAgentReconcilerTest {

	private KubernetesClient client;
	private AiAgentReconciler reconciler;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		client = mock(KubernetesClient.class);
		reconciler = new AiAgentReconciler(client);

		// Mock Apps API (Deployments)
		AppsAPIGroupDSL appsMock = mock(AppsAPIGroupDSL.class);
		MixedOperation deploymentsMock = mock(MixedOperation.class);
		NonNamespaceOperation deployNsMock = mock(NonNamespaceOperation.class);
		RollableScalableResource deployResMock = mock(RollableScalableResource.class);

		doReturn(appsMock).when(client).apps();
		doReturn(deploymentsMock).when(appsMock).deployments();
		doReturn(deployNsMock).when(deploymentsMock).inNamespace(anyString());
		doReturn(deployResMock).when(deployNsMock).resource(any());
		RollableScalableResource existingDeployMock = mock(RollableScalableResource.class);
		doReturn(existingDeployMock).when(deployNsMock).withName(anyString());
		doReturn(null).when(existingDeployMock).get();
		// Mock Services API
		MixedOperation servicesMock = mock(MixedOperation.class);
		NonNamespaceOperation svcNsMock = mock(NonNamespaceOperation.class);
		ServiceResource svcResMock = mock(ServiceResource.class);

		doReturn(servicesMock).when(client).services();
		doReturn(svcNsMock).when(servicesMock).inNamespace(anyString());
		doReturn(svcResMock).when(svcNsMock).resource(any());

		// Mock Network API (Ingresses)
		NetworkAPIGroupDSL netMock = mock(NetworkAPIGroupDSL.class, RETURNS_DEEP_STUBS);
		MixedOperation ingressesMock = mock(MixedOperation.class);
		NonNamespaceOperation ingNsMock = mock(NonNamespaceOperation.class);
		Resource ingResMock = mock(Resource.class);

		doReturn(netMock).when(client).network();
		when(netMock.v1().ingresses()).thenReturn(ingressesMock);
		doReturn(ingNsMock).when(ingressesMock).inNamespace(anyString());
		doReturn(ingResMock).when(ingNsMock).resource(any());

		// Mock LlmProvider CR lookup
		MixedOperation llmProvidersMock = mock(MixedOperation.class);
		NonNamespaceOperation llmNsMock = mock(NonNamespaceOperation.class);
		Resource llmResMock = mock(Resource.class);

		doReturn(llmProvidersMock).when(client).resources(LlmProvider.class);
		doReturn(llmNsMock).when(llmProvidersMock).inNamespace(anyString());
		doReturn(llmResMock).when(llmNsMock).withName(anyString());

		var provider = new LlmProvider();
		provider.setMetadata(new ObjectMetaBuilder().withName("openai-provider").withNamespace("default").build());
		provider.setSpec(new LlmProviderSpec("OPENAI", "https://api.openai.com/v1", null, "gpt-4o", 0.7, 2048, 0.0, 0.0,
				List.of()));
		doReturn(provider).when(llmResMock).get();
	}

	@Test
	@DisplayName("Should create managed Deployment, Service, & Ingress and update status to Ready")
	void testReconcileAiAgentFull() {
		var agent = new AiAgent();
		agent.setMetadata(new ObjectMetaBuilder().withName("support-agent").withNamespace("default").withUid("uid-123")
				.withGeneration(1L).build());
		agent.setSpec(new AiAgentSpec(new ProviderRef("openai-provider", "default"), "gpt-4o", "System prompt test",
				"User query test", List.of(), List.of(),
				List.of(new ToolDefinition("calculator", "Math", true, Map.of())), List.of(), // toolSources
				List.of(), // mcpServers
				null, // guardrails
				null, // a2a
				new IngressSpec(true, "agent.tuluat.com", "/", "Prefix", "nginx", Map.of(), null), 1));

		UpdateControl<AiAgent> control = reconciler.reconcile(agent, null);

		assertNotNull(control);
		assertNotNull(agent.getStatus());
		assertEquals("Ready", agent.getStatus().phase());
		assertEquals("http://agent.tuluat.com/", agent.getStatus().ingressUrl());
		assertEquals(List.of("calculator"), agent.getStatus().activeTools());
	}
}
