package com.tuluat.operator.reconciler;

import com.tuluat.crd.session.WorkflowSession;
import com.tuluat.crd.session.WorkflowSessionSpec;
import com.tuluat.crd.session.WorkflowSessionStatus;
import com.tuluat.crd.workflow.AiWorkflow;
import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.workflow.WorkflowExecutionService;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowSessionReconcilerTest {

	private WorkflowExecutionService executionService;
	private KubernetesClient client;
	private WorkflowSessionReconciler reconciler;

	private MixedOperation workflowsMock;
	private NonNamespaceOperation workflowsInNsMock;
	private Resource workflowResourceMock;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		executionService = mock(WorkflowExecutionService.class);
		client = mock(KubernetesClient.class);
		reconciler = new WorkflowSessionReconciler(executionService, client);

		workflowsMock = mock(MixedOperation.class);
		workflowsInNsMock = mock(NonNamespaceOperation.class);
		workflowResourceMock = mock(Resource.class);

		when(client.resources(AiWorkflow.class)).thenReturn(workflowsMock);
		when(workflowsMock.inNamespace(anyString())).thenReturn(workflowsInNsMock);
		when(workflowsInNsMock.withName(anyString())).thenReturn(workflowResourceMock);
	}

	@Test
	@DisplayName("Should trigger workflow execution when WorkflowSession is PENDING")
	void testReconcilePendingSession() {
		var workflow = new AiWorkflow();
		workflow.setMetadata(new ObjectMetaBuilder().withName("research-wf").withNamespace("default").build());
		workflow.setSpec(new AiWorkflowSpec());
		when(workflowResourceMock.get()).thenReturn(workflow);

		UUID sessionId = UUID.randomUUID();
		WorkflowSessionEntity entity = new WorkflowSessionEntity();
		entity.setSessionId(sessionId);
		entity.setStatus("RUNNING");
		entity.setCurrentNodeId("start-node");

		when(executionService.startSession(eq("research-wf"), any(), eq("input data"), eq(10))).thenReturn(entity);

		var session = new WorkflowSession();
		session.setMetadata(new ObjectMetaBuilder().withName("session-1").withNamespace("default").build());
		WorkflowSessionSpec spec = new WorkflowSessionSpec();
		spec.setWorkflowRef("research-wf");
		spec.setInput("input data");
		session.setSpec(spec);

		UpdateControl<WorkflowSession> control = reconciler.reconcile(session, null);

		assertNotNull(control);
		assertNotNull(session.getStatus());
		assertEquals(sessionId.toString(), session.getStatus().getSessionId());
		assertEquals("RUNNING", session.getStatus().getPhase());
		assertEquals("start-node", session.getStatus().getCurrentNode());
	}

	@Test
	@DisplayName("Should skip reconciliation when status is already COMPLETED")
	void testReconcileCompletedSession() {
		var session = new WorkflowSession();
		session.setMetadata(new ObjectMetaBuilder().withName("session-1").withNamespace("default").build());
		WorkflowSessionStatus status = new WorkflowSessionStatus();
		status.setPhase("COMPLETED");
		session.setStatus(status);

		UpdateControl<WorkflowSession> control = reconciler.reconcile(session, null);

		assertNotNull(control);
		assertFalse(control.isPatchStatus());
	}
}
