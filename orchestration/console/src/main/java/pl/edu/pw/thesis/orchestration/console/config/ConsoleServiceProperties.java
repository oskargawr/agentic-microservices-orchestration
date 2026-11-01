package pl.edu.pw.thesis.orchestration.console.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import pl.edu.pw.thesis.orchestration.varianta.ServiceEndpoints;

@ConfigurationProperties(prefix = "thesis.services")
public class ConsoleServiceProperties {

    private String inventoryBaseUrl = "http://localhost:8081";
    private String paymentBaseUrl = "http://localhost:8082";
    private String shippingBaseUrl = "http://localhost:8083";

    public String getInventoryBaseUrl() {
        return inventoryBaseUrl;
    }

    public void setInventoryBaseUrl(String inventoryBaseUrl) {
        this.inventoryBaseUrl = inventoryBaseUrl;
    }

    public String getPaymentBaseUrl() {
        return paymentBaseUrl;
    }

    public void setPaymentBaseUrl(String paymentBaseUrl) {
        this.paymentBaseUrl = paymentBaseUrl;
    }

    public String getShippingBaseUrl() {
        return shippingBaseUrl;
    }

    public void setShippingBaseUrl(String shippingBaseUrl) {
        this.shippingBaseUrl = shippingBaseUrl;
    }

    public ServiceEndpoints toEndpoints() {
        return new ServiceEndpoints(inventoryBaseUrl, paymentBaseUrl, shippingBaseUrl);
    }
}
