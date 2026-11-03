package pl.edu.pw.thesis.experiment.runner.g5;

import io.temporal.api.workflowservice.v1.DescribeTaskQueueRequest;
import io.temporal.api.workflowservice.v1.DescribeTaskQueueResponse;

/** Testable seam for {@code DescribeTaskQueue}. */
@FunctionalInterface
public interface G5DescribeTaskQueueClient {

    DescribeTaskQueueResponse describe(DescribeTaskQueueRequest request);
}
