package pl.edu.pw.thesis.experiment.runner.fault;

public record FaultVerifyResult(boolean ok, String detail) {

    public static FaultVerifyResult success() {
        return new FaultVerifyResult(true, "ok");
    }

    public static FaultVerifyResult failed(String detail) {
        return new FaultVerifyResult(false, detail);
    }
}
