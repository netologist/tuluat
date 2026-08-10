package com.tuluat.engine.config;

import com.tuluat.engine.temporal.GraphNodeActivitiesImpl;
import com.tuluat.engine.temporal.WorkflowSessionTemporalWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class TemporalConfig {
@Value("${spring.temporal.target:temporal-service:7233}")
	private String temporalTarget;

	public static final String TASK_QUEUE = "AI_WORKFLOW_TASK_QUEUE";

	@Bean
	public WorkflowServiceStubs workflowServiceStubs() {
		log.info("Connecting Temporal WorkflowServiceStubs to target: {}", temporalTarget);
		WorkflowServiceStubsOptions options = WorkflowServiceStubsOptions.newBuilder().setTarget(temporalTarget)
				.build();
		return WorkflowServiceStubs.newServiceStubs(options);
	}

	@Bean
	public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
		WorkflowClientOptions options = WorkflowClientOptions.newBuilder().setNamespace("default").build();
		return WorkflowClient.newInstance(serviceStubs, options);
	}

	@Bean
	public WorkerFactory workerFactory(WorkflowClient workflowClient, GraphNodeActivitiesImpl activities) {
		WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
		Worker worker = factory.newWorker(TASK_QUEUE);

		worker.registerWorkflowImplementationTypes(WorkflowSessionTemporalWorkflowImpl.class);
		worker.registerActivitiesImplementations(activities);

		factory.start();
		log.info("Temporal WorkerFactory started listening on task queue: {}", TASK_QUEUE);
		return factory;
	}
}
