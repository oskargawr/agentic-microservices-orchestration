package pl.edu.pw.thesis.experiment.runner.config;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Fail-closed JDBC allowlist (D-R12). Empty allowlist rejects all targets.
 * Matches normalized {@code jdbc:postgresql://host:port/database} forms.
 */
public final class JdbcAllowlist {

    private final Set<String> allowedNormalized;

    public JdbcAllowlist(Set<String> allowedJdbcUrls) {
        Objects.requireNonNull(allowedJdbcUrls, "allowedJdbcUrls");
        Set<String> normalized = new LinkedHashSet<>();
        for (String url : allowedJdbcUrls) {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("allowlist entries must be non-blank");
            }
            normalized.add(normalize(url.trim()));
        }
        this.allowedNormalized = Set.copyOf(normalized);
    }

    public static JdbcAllowlist of(String... urls) {
        return new JdbcAllowlist(Set.of(urls));
    }

    public void requireAllowed(String jdbcUrl) {
        if (allowedNormalized.isEmpty()) {
            throw new UnsafeEnvironmentException(
                    "WRONG_DATABASE",
                    "JDBC allowlist is empty — fail-closed; refusing destructive operations");
        }
        String normalized = normalize(jdbcUrl);
        if (!allowedNormalized.contains(normalized)) {
            throw new UnsafeEnvironmentException(
                    "WRONG_DATABASE",
                    "JDBC URL not on allowlist: " + jdbcUrl);
        }
    }

    public boolean isEmpty() {
        return allowedNormalized.isEmpty();
    }

    /** Package + gate visible normalization of jdbc URLs (host/port/database). */
    public static String normalize(String jdbcUrl) {
        String url = jdbcUrl.trim();
        if (!url.toLowerCase(Locale.ROOT).startsWith("jdbc:")) {
            throw new IllegalArgumentException("not a JDBC URL: " + jdbcUrl);
        }
        // jdbc:postgresql://host:port/db?params → URI-like without jdbc:
        String withoutJdbc = url.substring("jdbc:".length());
        // strip query
        int q = withoutJdbc.indexOf('?');
        if (q >= 0) {
            withoutJdbc = withoutJdbc.substring(0, q);
        }
        URI uri = URI.create(withoutJdbc);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port < 0 && "postgresql".equals(scheme)) {
            port = 5432;
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        // drop trailing slash noise
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return scheme + "://" + host + ":" + port + "/" + path.toLowerCase(Locale.ROOT);
    }
}
