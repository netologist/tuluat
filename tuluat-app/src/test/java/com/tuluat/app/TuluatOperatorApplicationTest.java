package com.tuluat.app;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
		"spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"AGENT_NAME=test-agent"})
class TuluatOperatorApplicationTest {

	@MockitoBean
	private KubernetesClient kubernetesClient;

	@MockitoBean
	private WorkflowServiceStubs workflowServiceStubs;

	@MockitoBean
	private WorkflowClient workflowClient;

	@MockitoBean
	private WorkerFactory workerFactory;

	@Test
	@DisplayName("Spring Boot 4 Application Context loads successfully")
	void contextLoads() {
		assertNotNull(kubernetesClient, "KubernetesClient bean should be mocked and injected");
	}
}
