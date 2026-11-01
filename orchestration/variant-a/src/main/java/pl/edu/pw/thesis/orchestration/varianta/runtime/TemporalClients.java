package pl.edu.pw.thesis.orchestration.varianta.runtime;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

/** Factory for Temporal stubs/client against a real server. */
public final class TemporalClients {

    private TemporalClients() {
    }

    public static WorkflowServiceStubs newServiceStubs(TemporalSettings settings) {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(settings.target())
                        .build());
    }

    public static WorkflowClient newWorkflowClient(
            WorkflowServiceStubs stubs, TemporalSettings settings) {
        return WorkflowClient.newInstance(
                stubs,
                WorkflowClientOptions.newBuilder()
                        .setNamespace(settings.namespace())
                        .build());
    }
}
