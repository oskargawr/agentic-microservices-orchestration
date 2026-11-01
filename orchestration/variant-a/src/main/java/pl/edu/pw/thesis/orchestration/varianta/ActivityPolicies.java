package pl.edu.pw.thesis.orchestration.varianta;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import java.time.Duration;

/**
 * Shared activity options for Variant A. See {@code docs/architecture/VARIANT_A_HAPPY_PATH.md}.
 */
public final class ActivityPolicies {

    public static final String BUSINESS_FAILURE_TYPE = "BUSINESS";

    private ActivityPolicies() {
    }

    public static ActivityOptions defaultOptions() {
        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setScheduleToCloseTimeout(Duration.ofSeconds(60))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofMillis(200))
                        .setMaximumInterval(Duration.ofSeconds(2))
                        .setBackoffCoefficient(2.0)
                        .setMaximumAttempts(5)
                        .setDoNotRetry(BUSINESS_FAILURE_TYPE)
                        .build())
                .build();
    }
}
