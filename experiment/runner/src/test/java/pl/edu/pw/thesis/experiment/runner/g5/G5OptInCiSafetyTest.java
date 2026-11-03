package pl.edu.pw.thesis.experiment.runner.g5;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Ordinary CI / Gradle must not enable live G5. */
class G5OptInCiSafetyTest {

    @Test
    void ordinaryTestJvm_doesNotRequestLiveG5() {
        assertThat(System.getenv("THESIS_RUN_G5")).isNull();
        assertThat(G5OptIn.liveG5Requested()).isFalse();
        assertThat(RunnerLiveTemporalG5IT.liveG5Enabled()).isFalse();
    }

    @Test
    void ciWorkflow_doesNotSetThesisRunG5() throws Exception {
        Path workflow = Path.of(".github/workflows/ci.yml");
        if (!Files.exists(workflow)) {
            // alternate naming used in this repo
            workflow = Path.of(".github/workflows/CI.yml");
        }
        // Discover any workflow yaml under .github/workflows
        try (var stream = Files.list(Path.of(".github/workflows"))) {
            boolean anyMentions = stream
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".yml") || n.endsWith(".yaml");
                    })
                    .anyMatch(p -> {
                        try {
                            String text = Files.readString(p);
                            return text.contains("THESIS_RUN_G5");
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            assertThat(anyMentions)
                    .as("CI workflows must not set THESIS_RUN_G5")
                    .isFalse();
        }
    }

    @Test
    void gradleProperties_doNotEnableG5() throws Exception {
        Path props = Path.of("gradle.properties");
        if (Files.exists(props)) {
            assertThat(Files.readString(props)).doesNotContain("THESIS_RUN_G5");
        }
    }
}
