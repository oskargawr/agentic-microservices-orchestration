package pl.edu.pw.thesis.orchestration.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import pl.edu.pw.thesis.orchestration.varianta.runtime.TemporalSettings;

@ConfigurationProperties(prefix = "thesis.temporal")
public class ThesisTemporalProperties {

    private String target = TemporalSettings.DEFAULT_TARGET;
    private String namespace = TemporalSettings.DEFAULT_NAMESPACE;

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

    public TemporalSettings toSettings() {
        return new TemporalSettings(target, namespace);
    }
}
