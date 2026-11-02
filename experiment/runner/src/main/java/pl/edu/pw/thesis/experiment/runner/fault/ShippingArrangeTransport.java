package pl.edu.pw.thesis.experiment.runner.fault;

import java.io.IOException;

/** Minimal HTTP transport for the F2 shipping injection preflight. */
public interface ShippingArrangeTransport {

    record Response(int statusCode, String body) {}

    Response postArrange(String idempotencyKey, String faultHeader, String jsonBody)
            throws IOException, InterruptedException;
}
