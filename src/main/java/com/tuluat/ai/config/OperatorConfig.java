package com.tuluat.ai.config;

import com.tuluat.ai.reconciler.AiAgentReconciler;
import com.tuluat.ai.reconciler.LlmProviderReconciler;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.util.concurrent.Executors;

@Configuration
public class OperatorConfig {

    @Bean
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }

    /**
     * Run Operator JOSDK only in Operator deployment (when AGENT_NAME env var is not set).
     */
    @Bean(destroyMethod = "stop")
    @ConditionalOnExpression("environment.getProperty('AGENT_NAME') == null")
    public Operator operator(KubernetesClient client, LlmProviderReconciler providerReconciler, AiAgentReconciler agentReconciler) {
        Operator operator = new Operator(overrider -> overrider.withKubernetesClient(client));
        operator.register(providerReconciler);
        operator.register(agentReconciler);
        operator.start();
        return operator;
    }

    /**
     * Configure Spring Async tasks to run on Virtual Threads.
     */
    @Bean
    public AsyncTaskExecutor applicationTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
