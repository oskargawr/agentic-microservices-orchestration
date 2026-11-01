package pl.edu.pw.thesis.orchestration.console;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import pl.edu.pw.thesis.orchestration.console.config.ConsoleCorsProperties;
import pl.edu.pw.thesis.orchestration.console.config.ConsoleServiceProperties;
import pl.edu.pw.thesis.orchestration.console.config.ConsoleTemporalProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    ConsoleTemporalProperties.class,
    ConsoleServiceProperties.class,
    ConsoleCorsProperties.class
})
public class ConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsoleApplication.class, args);
    }
}
