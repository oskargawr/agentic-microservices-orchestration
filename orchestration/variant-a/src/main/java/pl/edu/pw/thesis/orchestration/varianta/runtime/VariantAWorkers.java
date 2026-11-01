package pl.edu.pw.thesis.orchestration.varianta.runtime;

import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import pl.edu.pw.thesis.orchestration.varianta.HttpOrderFulfillmentActivities;
import pl.edu.pw.thesis.orchestration.varianta.OrderFulfillmentWorkflow;
import pl.edu.pw.thesis.orchestration.varianta.OrderFulfillmentWorkflowImpl;
import pl.edu.pw.thesis.orchestration.varianta.ServiceEndpoints;

/** Registers Variant A workflow + HTTP activities on the shared task queue. */
public final class VariantAWorkers {

    private VariantAWorkers() {
    }

    public static WorkerFactory startWorkerFactory(
            WorkflowClient client, ServiceEndpoints endpoints) {
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(OrderFulfillmentWorkflow.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(OrderFulfillmentWorkflowImpl.class);
        worker.registerActivitiesImplementations(new HttpOrderFulfillmentActivities(endpoints));
        factory.start();
        return factory;
    }
}
