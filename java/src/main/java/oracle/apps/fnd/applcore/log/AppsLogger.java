package oracle.apps.fnd.applcore.log;

import java.util.logging.Logger;

/**
 * Local-dev stub that mirrors the API surface of Fusion's real
 * {@code oracle.apps.fnd.applcore.log.AppsLogger}.
 *
 * <p>Files like {@code ConnectionHelper} and {@code FusionAiProvider} log via
 * {@code AppsLogger} because that is the only severity channel the Fusion
 * platform honors at runtime. On a developer laptop the real class is not on
 * the classpath, so this stub stands in and delegates to
 * {@link java.util.logging.Logger} so local builds compile and tests run.</p>
 *
 * <h3>Deployment</h3>
 * <ul>
 *   <li><b>local-dev</b> profile (default): this stub is compiled and used.</li>
 *   <li><b>fusion</b> profile: this file is excluded from compilation so the
 *       real {@code AppsLogger} from the Fusion classpath is picked up at
 *       runtime. The public API must stay byte-compatible with Fusion's class;
 *       see the level constants and {@code write}/{@code isEnabled} overloads
 *       below.</li>
 * </ul>
 *
 * <h3>API surface (must match Fusion)</h3>
 * <ul>
 *   <li>Level constants: {@link #SEVERE}, {@link #WARNING}, {@link #INFO},
 *       {@link #CONFIG}, {@link #FINE}, {@link #FINER}, {@link #FINEST} — typed
 *       as {@link java.util.logging.Level} so callers can pass
 *       {@code AppsLogger.INFO} anywhere a JUL level is expected.</li>
 *   <li>{@link #isEnabled(java.util.logging.Level)}</li>
 *   <li>{@link #write(Object, String, java.util.logging.Level)}</li>
 *   <li>{@link #write(Object, Throwable, java.util.logging.Level)}</li>
 * </ul>
 *
 * <p>The {@code source} argument accepts either a {@link Class} object
 * (e.g. {@code ConnectionHelper.class}) or an instance (e.g. {@code this}) —
 * both Fusion callers and our callers use both forms. The logger name is
 * derived from whichever form was passed.</p>
 */
public final class AppsLogger {

    // ─── Level constants — java.util.logging.Level instances ────────────────
    // Declared as java.util.logging.Level so existing call sites like
    //     AppsLogger.isEnabled(AppsLogger.FINER)
    //     AppsLogger.write(this, msg, AppsLogger.INFO)
    // compile without any import or cast churn.

    public static final java.util.logging.Level SEVERE  = java.util.logging.Level.SEVERE;
    public static final java.util.logging.Level WARNING = java.util.logging.Level.WARNING;
    public static final java.util.logging.Level INFO    = java.util.logging.Level.INFO;
    public static final java.util.logging.Level CONFIG  = java.util.logging.Level.CONFIG;
    public static final java.util.logging.Level FINE    = java.util.logging.Level.FINE;
    public static final java.util.logging.Level FINER   = java.util.logging.Level.FINER;
    public static final java.util.logging.Level FINEST  = java.util.logging.Level.FINEST;

    private AppsLogger() {
        // static-only utility — no instances
    }

    // ─── API surface ────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if a log message at {@code level} would actually be
     * emitted by the underlying logger. Callers use this to short-circuit
     * expensive string concatenation:
     *
     * <pre>{@code
     * if (AppsLogger.isEnabled(AppsLogger.FINER)) {
     *     AppsLogger.write(this, "payload=" + heavyToString(), AppsLogger.FINER);
     * }
     * }</pre>
     *
     * <p>The stub resolves the check against a root {@code java.util.logging}
     * logger so {@code -Djava.util.logging.config.file=...} and standard
     * {@code logging.properties} work as expected during local dev.</p>
     */
    public static boolean isEnabled(java.util.logging.Level level) {
        if (level == null) {
            return false;
        }
        return Logger.getLogger("").isLoggable(level);
    }

    /**
     * Writes a plain-text log record at the given level. {@code source} may be
     * a {@link Class} (used directly as the logger name) or any instance
     * (its runtime class is used). A null source falls back to this stub's
     * own logger name.
     */
    public static void write(Object source, String message, java.util.logging.Level level) {
        if (level == null) {
            level = INFO;
        }
        Logger logger = Logger.getLogger(loggerName(source));
        if (logger.isLoggable(level)) {
            logger.log(level, message);
        }
    }

    /**
     * Writes a {@link Throwable} (usually an {@code Exception}) at the given
     * level. The stack trace is recorded via the JUL
     * {@link java.util.logging.LogRecord#setThrown} mechanism so it shows up
     * the same way as a {@code logger.log(level, msg, throwable)} call.
     */
    public static void write(Object source, Throwable thrown, java.util.logging.Level level) {
        if (level == null) {
            level = WARNING;
        }
        Logger logger = Logger.getLogger(loggerName(source));
        if (logger.isLoggable(level)) {
            String message = thrown != null ? thrown.toString() : "null throwable";
            logger.log(level, message, thrown);
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static String loggerName(Object source) {
        if (source == null) {
            return AppsLogger.class.getName();
        }
        if (source instanceof Class<?>) {
            return ((Class<?>) source).getName();
        }
        return source.getClass().getName();
    }
}
