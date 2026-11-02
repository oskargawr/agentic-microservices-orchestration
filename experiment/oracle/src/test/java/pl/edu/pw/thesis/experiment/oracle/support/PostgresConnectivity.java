package pl.edu.pw.thesis.experiment.oracle.support;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;

/**
 * Decides whether a JDBC {@link SQLException} means PostgreSQL is unreachable
 * (safe to skip integration tests) vs a configuration/auth/SQL problem (must fail).
 *
 * <p>Uses SQLState class {@code 08} (connection exception) and common network causes.
 * Does <strong>not</strong> treat auth failures ({@code 28xxx}) as offline.
 */
public final class PostgresConnectivity {

    private PostgresConnectivity() {}

    public static boolean isUnreachable(SQLException exception) {
        for (SQLException cur = exception; cur != null; cur = cur.getNextException()) {
            String state = cur.getSQLState();
            if (state != null && state.startsWith("08")) {
                return true;
            }
            if (isNetworkCause(cur)) {
                return true;
            }
        }
        return isNetworkCause(exception);
    }

    private static boolean isNetworkCause(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof ConnectException
                    || t instanceof UnknownHostException
                    || t instanceof NoRouteToHostException
                    || t instanceof PortUnreachableException
                    || t instanceof SocketTimeoutException) {
                return true;
            }
            // Broad SocketException only when no SQLState was decisive; avoid treating
            // arbitrary wrapped errors as offline solely by message text.
            if (t instanceof SocketException && !(t instanceof SocketTimeoutException)) {
                // ConnectException extends SocketException and is already handled.
                // Other SocketExceptions (e.g. broken pipe mid-flight) are connectivity-ish
                // for a probe connection attempt.
                return true;
            }
        }
        return false;
    }
}
