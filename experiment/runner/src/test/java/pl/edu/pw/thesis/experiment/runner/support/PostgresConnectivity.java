package pl.edu.pw.thesis.experiment.runner.support;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;

/** Same unreachable probe policy as experiment:oracle IT support. */
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
            if (t instanceof SocketException) {
                return true;
            }
        }
        return false;
    }
}
