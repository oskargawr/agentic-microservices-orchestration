package pl.edu.pw.thesis.shipping.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "thesis.shipping")
public class ShippingProperties {

    /**
     * When false, fault headers are ignored. Enable only for local experiments / tests.
     */
    private boolean faultInjectionEnabled = false;

    public boolean isFaultInjectionEnabled() {
        return faultInjectionEnabled;
    }

    public void setFaultInjectionEnabled(boolean faultInjectionEnabled) {
        this.faultInjectionEnabled = faultInjectionEnabled;
    }
}
