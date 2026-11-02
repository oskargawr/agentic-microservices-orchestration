package pl.edu.pw.thesis.shipping.fault;

/**
 * Deterministic test/experiment fault codes via header {@code X-Thesis-Fault}.
 * Only honored when {@code thesis.shipping.fault-injection-enabled=true}.
 *
 * <p>Shipping-specific — do not reuse payment {@code ThesisFault} types.
 */
public enum ShippingFault {
    /** F2: reject arrange before shipment insert. */
    ARRANGE_REJECTED;

    public static final String HEADER = "X-Thesis-Fault";

    /**
     * @return parsed fault or empty if header absent/blank
     * @throws IllegalArgumentException if header is non-blank but not a known shipping fault
     */
    public static java.util.Optional<ShippingFault> parseOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(ShippingFault.valueOf(raw.trim()));
    }
}
