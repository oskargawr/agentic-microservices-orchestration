package pl.edu.pw.thesis.experiment.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Guardrails: no Temporal / Spring AI / LLM coupling in the oracle module sources. */
class OracleModuleConstraintsTest {

    @Test
    void sourcesMustNotDependOnTemporalOrLlm() throws IOException {
        Path root = Path.of("src/main/java");
        assertThat(root).exists();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    String src = Files.readString(p);
                    assertThat(src)
                            .as(p.toString())
                            .doesNotContain("io.temporal")
                            .doesNotContain("spring.ai")
                            .doesNotContain("openai")
                            .doesNotContain("BusinessConsistencyCheck");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Test
    void buildFileMustNotPullTemporal() throws IOException {
        String gradle = Files.readString(Path.of("build.gradle.kts"));
        assertThat(gradle).doesNotContain("temporal").doesNotContain("spring-ai");
    }
}
