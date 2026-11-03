package pl.edu.pw.thesis.experiment.runner.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import pl.edu.pw.thesis.experiment.runner.accept.MatrixSummary;
import pl.edu.pw.thesis.experiment.runner.fault.CanonicalFaultProfiles;
import pl.edu.pw.thesis.experiment.runner.model.ExperimentDefinition;
import pl.edu.pw.thesis.experiment.runner.model.TrialRecord;
import pl.edu.pw.thesis.experiment.runner.model.TrialSpec;
import pl.edu.pw.thesis.experiment.runner.time.RunnerClock;

/**
 * S4-A: executes Variant A fault-profile cells serially (n repetitions × profiles) using an
 * existing {@link TrialRunner} (arm→verify→execute→oracle→disarm→reset stays inside the runner).
 */
public final class MatrixExperimentDriver {

    private final ExperimentDefinition experiment;
    private final TrialRunner trialRunner;
    private final RunnerClock clock;

    public MatrixExperimentDriver(
            ExperimentDefinition experiment, TrialRunner trialRunner, RunnerClock clock) {
        this.experiment = Objects.requireNonNull(experiment, "experiment");
        this.trialRunner = Objects.requireNonNull(trialRunner, "trialRunner");
        this.clock = Objects.requireNonNull(clock, "clock");
        CanonicalFaultProfiles.normalizeSchedule(experiment.faultProfileIds());
    }

    public MatrixRunResult runAll() {
        List<String> profiles = CanonicalFaultProfiles.normalizeSchedule(experiment.faultProfileIds());
        List<TrialRecord> records = new ArrayList<>();
        boolean aborted = false;
        String abortReason = null;

        outer:
        for (String profileId : profiles) {
            for (int rep = 0; rep < experiment.n(); rep++) {
                if (trialRunner.isFurtherTrialsBlocked()) {
                    aborted = true;
                    abortReason = "PREVIOUS_TRIAL_BLOCKED";
                    break outer;
                }
                UUID orderId = UUID.randomUUID();
                String trialId = "trial-" + profileId + "-r" + rep + "-" + orderId;
                TrialSpec spec = TrialSpec.forVariantA(
                        experiment.experimentId(),
                        trialId,
                        profileId,
                        rep,
                        orderId,
                        experiment.businessDefaults());
                TrialRecord record = trialRunner.run(spec);
                records.add(record);
                if (trialRunner.wasOperatorInterrupted()) {
                    aborted = true;
                    abortReason = "OPERATOR_INTERRUPT";
                    break outer;
                }
                if (trialRunner.isFurtherTrialsBlocked()) {
                    aborted = true;
                    abortReason = "FURTHER_TRIALS_BLOCKED";
                    break outer;
                }
            }
        }

        int plannedCells = profiles.size() * experiment.n();
        MatrixSummary summary = MatrixSummary.fromTrials(
                experiment.experimentId(),
                experiment.protocolId(),
                experiment.gitSha(),
                clock.now(),
                plannedCells,
                records);
        return new MatrixRunResult(records, summary, aborted, abortReason);
    }

    public record MatrixRunResult(
            List<TrialRecord> trials,
            MatrixSummary summary,
            boolean aborted,
            String abortReason) {
        public MatrixRunResult {
            trials = List.copyOf(trials);
            Objects.requireNonNull(summary, "summary");
        }
    }
}
