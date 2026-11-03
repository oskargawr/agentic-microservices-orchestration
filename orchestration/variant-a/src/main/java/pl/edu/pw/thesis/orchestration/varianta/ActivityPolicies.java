package pl.edu.pw.thesis.orchestration.varianta;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import java.time.Duration;

/**
 * Shared activity options for Variant A (D-202-1 engineering defaults).
 * See {@code docs/architecture/VARIANT_A_RETRIES_TIMEOUTS.md}.
 */
public final class ActivityPolicies {

    public static final String BUSINESS_FAILURE_TYPE = "BUSINESS";
    public static final String COMPENSATION_INCOMPLETE_TYPE = "COMPENSATION_INCOMPLETE";

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
                        .setDoNotRetry(BUSINESS_FAILURE_TYPE, COMPENSATION_INCOMPLETE_TYPE)
                        .build())
                .build();
    }

    /** Compensation activities: still retry transient HTTP; never spin on BUSINESS. */
    public static ActivityOptions compensationOptions() {
        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setScheduleToCloseTimeout(Duration.ofSeconds(90))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofMillis(200))
                        .setMaximumInterval(Duration.ofSeconds(2))
                        .setBackoffCoefficient(2.0)
                        .setMaximumAttempts(5)
                        .setDoNotRetry(BUSINESS_FAILURE_TYPE, COMPENSATION_INCOMPLETE_TYPE)
                        .build())
                .build();
    }

    /**
     * D-208 B: at most one idempotent re-POST per missing create id (no nested Temporal retry
     * storm). Separate from {@link #defaultOptions()} — {@code maximumAttempts=1} means a single
     * recovery execution, not “one retry after an initial attempt.”
     */
    public static ActivityOptions lostIdRecoveryOptions() {
        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setScheduleToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(1)
                        .setDoNotRetry(BUSINESS_FAILURE_TYPE, COMPENSATION_INCOMPLETE_TYPE)
                        .build())
                .build();
    }
}
