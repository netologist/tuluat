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

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
		"spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=update", "AGENT_NAME=test-agent", "embabel.models.default-llm=deepseek-chat",
		"embabel.agent.platform.models.openai.custom.api-key=sk-test-key",
		"embabel.agent.platform.models.openai.custom.base-url=https://api.deepseek.com",
		"embabel.agent.platform.models.openai.custom.models=deepseek-chat", "spring.ai.openai.api-key=sk-test-key",
		"spring.ai.openai.base-url=https://api.deepseek.com"})
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
