package pl.edu.pw.thesis.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "thesis.payment")
public class PaymentProperties {

    /**
     * When false, fault headers are ignored and charge defaults to CAPTURED.
     * Enable only for local experiments / tests — never a production control plane.
     */
    private boolean faultInjectionEnabled = false;

    public boolean isFaultInjectionEnabled() {
        return faultInjectionEnabled;
    }

    public void setFaultInjectionEnabled(boolean faultInjectionEnabled) {
        this.faultInjectionEnabled = faultInjectionEnabled;
    }
}
