package pl.edu.pw.thesis.experiment.runner.fault;

import java.io.IOException;
import java.util.UUID;

/** HTTP probes for payment charge / resolve / GET (F1-P and F3 preflight). */
public interface PaymentProbeTransport extends PaymentChargeTransport {

    Response postResolve(UUID paymentId, String faultHeader) throws IOException, InterruptedException;

    /** {@code faultHeader} may be {@code null} (omit {@code X-Thesis-Fault}). */
    Response getPayment(UUID paymentId, String faultHeader) throws IOException, InterruptedException;
}
