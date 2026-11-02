package pl.edu.pw.thesis.orchestration.varianta;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T-202-1 — exact ActivityOptions values (D-202-1). */
class ActivityPoliciesTest {

    @Test
    void t202_1_forwardOptionsMatchEngineeringDefaults() {
        ActivityOptions options = ActivityPolicies.defaultOptions();
        assertThat(options.getStartToCloseTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(options.getScheduleToCloseTimeout()).isEqualTo(Duration.ofSeconds(60));

        RetryOptions retry = options.getRetryOptions();
        assertThat(retry.getInitialInterval()).isEqualTo(Duration.ofMillis(200));
        assertThat(retry.getMaximumInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(retry.getBackoffCoefficient()).isEqualTo(2.0);
        assertThat(retry.getMaximumAttempts()).isEqualTo(5);
        assertThat(doNotRetrySet(retry)).containsExactlyInAnyOrder(
                ActivityPolicies.BUSINESS_FAILURE_TYPE,
                ActivityPolicies.COMPENSATION_INCOMPLETE_TYPE);
    }

    @Test
    void t202_1_compensationOptionsMatchEngineeringDefaults() {
        ActivityOptions options = ActivityPolicies.compensationOptions();
        assertThat(options.getStartToCloseTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(options.getScheduleToCloseTimeout()).isEqualTo(Duration.ofSeconds(90));

        RetryOptions retry = options.getRetryOptions();
        assertThat(retry.getInitialInterval()).isEqualTo(Duration.ofMillis(200));
        assertThat(retry.getMaximumInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(retry.getBackoffCoefficient()).isEqualTo(2.0);
        assertThat(retry.getMaximumAttempts()).isEqualTo(5);
        assertThat(doNotRetrySet(retry)).containsExactlyInAnyOrder(
                ActivityPolicies.BUSINESS_FAILURE_TYPE,
                ActivityPolicies.COMPENSATION_INCOMPLETE_TYPE);
    }

    private static Set<String> doNotRetrySet(RetryOptions retry) {
        String[] types = retry.getDoNotRetry();
        return types == null ? Set.of() : new HashSet<>(Arrays.asList(types));
    }
}
