package pl.edu.pw.thesis.orchestration.worker;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.edu.pw.thesis.orchestration.varianta.OrderFulfillmentWorkflow;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalClients;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalSettings;
import pl.edu.pw.thesis.orchestration.varianta.runtime.VariantAWorkers;

@Component
public class WorkerRuntime {

    private static final Logger log = LoggerFactory.getLogger(WorkerRuntime.class);

    private final WorkflowServiceStubs stubs;
    private final WorkerFactory factory;

    public WorkerRuntime(ThesisTemporalProperties temporal, ThesisServiceProperties services) {
        TemporalSettings settings = temporal.toSettings();
        this.stubs = TemporalClients.newServiceStubs(settings);
        WorkflowClient client = TemporalClients.newWorkflowClient(stubs, settings);
        this.factory = VariantAWorkers.startWorkerFactory(client, services.toEndpoints());
        log.info(
                "Variant A worker started: target={} namespace={} taskQueue={} inventory={} payment={} shipping={}",
                settings.target(),
                settings.namespace(),
                OrderFulfillmentWorkflow.TASK_QUEUE,
                services.getInventoryBaseUrl(),
                services.getPaymentBaseUrl(),
                services.getShippingBaseUrl());
    }

    @PreDestroy
    void shutdown() {
        log.info("Shutting down Variant A worker");
        factory.shutdown();
        stubs.shutdown();
    }
}
