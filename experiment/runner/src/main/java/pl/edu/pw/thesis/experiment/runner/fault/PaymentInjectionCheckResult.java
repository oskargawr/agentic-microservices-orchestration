package pl.edu.pw.thesis.experiment.runner.fault;

public record PaymentInjectionCheckResult(boolean enabled, String detail) {

    public static PaymentInjectionCheckResult enabled(String detail) {
        return new PaymentInjectionCheckResult(true, detail);
    }

    public static PaymentInjectionCheckResult disabledOrUnknown(String detail) {
        return new PaymentInjectionCheckResult(false, detail);
    }
}
