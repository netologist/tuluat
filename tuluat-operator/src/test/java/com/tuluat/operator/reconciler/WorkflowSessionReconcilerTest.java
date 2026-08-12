package com.tuluat.operator.reconciler;

import com.tuluat.crd.session.WorkflowSession;
import com.tuluat.crd.session.WorkflowSessionSpec;
import com.tuluat.crd.session.WorkflowSessionStatus;
import com.tuluat.crd.workflow.AiWorkflow;
import com.tuluat.crd.workflow.AiWorkflowSpec;
import com.tuluat.engine.entity.WorkflowSessionEntity;
import com.tuluat.engine.entity.SessionStatus;
import com.tuluat.engine.workflow.WorkflowExecutionService;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;
@ExtendWith(MockitoExtension.class)
class WorkflowSessionReconcilerTest {

	@Mock
	private KubernetesClient kubernetesClient;
	@Mock
	private WorkflowExecutionService executionService;
	@Mock
	@SuppressWarnings("rawtypes")
	private MixedOperation workflowsMock;
	@Mock
	@SuppressWarnings("rawtypes")
	private MixedOperation workflowsInNsMock;
	@Mock
	private Resource<AiWorkflow> workflowResourceMock;
	@InjectMocks
	private WorkflowSessionReconciler reconciler;

	@BeforeEach
	void setUp() {
		lenient().when(kubernetesClient.resources(AiWorkflow.class)).thenReturn(workflowsMock);
		lenient().when(workflowsMock.inNamespace(anyString())).thenReturn(workflowsInNsMock);
		lenient().when(workflowsInNsMock.withName(anyString())).thenReturn(workflowResourceMock);
		lenient().when(workflowResourceMock.get()).thenReturn(null);
	}

	@Test
	@DisplayName("Should trigger workflow execution when WorkflowSession is PENDING")
	void testReconcilePendingSession() {
		var workflow = new AiWorkflow();
		workflow.setMetadata(new ObjectMetaBuilder().withName("research-wf").withNamespace("default").build());
		workflow.setSpec(new AiWorkflowSpec(null, null, null, null, null));
		when(workflowResourceMock.get()).thenReturn(workflow);

		UUID sessionId = UUID.randomUUID();
		WorkflowSessionEntity entity = new WorkflowSessionEntity();
		entity.setSessionId(sessionId);
		entity.setStatus(SessionStatus.RUNNING);
		entity.setCurrentNodeId("start-node");

		when(executionService.startSession(eq("research-wf"), any(), eq("input data"), eq(10))).thenReturn(entity);

		var session = new WorkflowSession();
		session.setMetadata(new ObjectMetaBuilder().withName("session-1").withNamespace("default").build());
		WorkflowSessionSpec spec = new WorkflowSessionSpec("research-wf", "input data", null);
		session.setSpec(spec);

		UpdateControl<WorkflowSession> control = reconciler.reconcile(session, null);

		assertNotNull(control);
		assertNotNull(session.getStatus());
		assertEquals(sessionId.toString(), session.getStatus().sessionId());
		assertEquals("RUNNING", session.getStatus().phase());
		assertEquals("start-node", session.getStatus().currentNode());
	}

	@Test
	@DisplayName("Should skip reconciliation when status is already COMPLETED")
	void testReconcileCompletedSession() {
		var session = new WorkflowSession();
		session.setMetadata(new ObjectMetaBuilder().withName("session-1").withNamespace("default").build());
		WorkflowSessionStatus status = new WorkflowSessionStatus(null, "COMPLETED", null, null, null, null);
		session.setStatus(status);

		UpdateControl<WorkflowSession> control = reconciler.reconcile(session, null);

		assertNotNull(control);
		assertFalse(control.isPatchStatus());
	}
}
