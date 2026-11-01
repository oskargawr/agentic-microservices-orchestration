package pl.edu.pw.thesis.orchestration.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Independent Temporal worker process for Variant A (TH-205.1).
 * Polls {@code variant-a-order-fulfillment} on the configured Temporal server.
 */
@SpringBootApplication
@EnableConfigurationProperties({ThesisTemporalProperties.class, ThesisServiceProperties.class})
public class VariantAWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(VariantAWorkerApplication.class, args);
    }
}
