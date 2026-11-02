package pl.edu.pw.thesis.experiment.runner.fault;

import java.io.IOException;

/** Minimal HTTP transport for the F1-P payment injection preflight (existing charge API only). */
public interface PaymentChargeTransport {

    record Response(int statusCode, String body) {}

    Response postCharge(String idempotencyKey, String faultHeader, String jsonBody)
            throws IOException, InterruptedException;
}
