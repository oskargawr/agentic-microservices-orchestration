package pl.edu.pw.thesis.orchestration.console.config;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalClients;

@Configuration
public class TemporalClientConfig {

    @Bean(destroyMethod = "shutdown")
    WorkflowServiceStubs workflowServiceStubs(ConsoleTemporalProperties properties) {
        return TemporalClients.newServiceStubs(properties.toSettings());
    }

    @Bean
    WorkflowClient workflowClient(
            WorkflowServiceStubs stubs, ConsoleTemporalProperties properties) {
        return TemporalClients.newWorkflowClient(stubs, properties.toSettings());
    }
}
