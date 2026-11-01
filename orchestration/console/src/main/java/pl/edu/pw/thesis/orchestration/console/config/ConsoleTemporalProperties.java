package pl.edu.pw.thesis.orchestration.console.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalSettings;

@ConfigurationProperties(prefix = "thesis.temporal")
public class ConsoleTemporalProperties {

    private String target = TemporalSettings.DEFAULT_TARGET;
    private String namespace = TemporalSettings.DEFAULT_NAMESPACE;
    private String uiBaseUrl = "http://localhost:8088";

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getUiBaseUrl() {
        return uiBaseUrl;
    }

    public void setUiBaseUrl(String uiBaseUrl) {
        this.uiBaseUrl = uiBaseUrl;
    }

    public TemporalSettings toSettings() {
        return new TemporalSettings(target, namespace);
    }
}
