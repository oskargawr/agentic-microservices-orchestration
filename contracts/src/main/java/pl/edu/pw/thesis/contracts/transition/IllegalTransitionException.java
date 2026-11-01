package pl.edu.pw.thesis.contracts.transition;

public final class IllegalTransitionException extends RuntimeException {

    public IllegalTransitionException(String message) {
        super(message);
    }
}
