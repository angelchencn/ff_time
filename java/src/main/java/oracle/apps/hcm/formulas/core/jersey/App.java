package oracle.apps.hcm.formulas.core.jersey;

import oracle.apps.fnd.applcore.log.AppsLogger;
import oracle.apps.hcm.formulas.core.jersey.config.*;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;

import java.net.URI;

public class App {

    private static final String BASE_URI = "http://0.0.0.0:8000/api/";

    public static void main(String[] args) throws Exception {
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(App.class,
                    "Starting FF Time Grizzly server at " + BASE_URI, AppsLogger.INFO);
        }

        HttpServer server;
        try {
            server = GrizzlyHttpServerFactory.createHttpServer(
                    URI.create(BASE_URI), new JerseyConfig());
        } catch (Throwable t) {
            // Bind failure, port conflict, or any startup crash — log with full
            // stack so ops can see WHY the process died rather than a bare
            // "exit 1" in the container logs.
            AppsLogger.write(App.class, t, AppsLogger.SEVERE);
            throw t;
        }

        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(App.class,
                    "FF Time server started at " + BASE_URI + "api/", AppsLogger.INFO);
            AppsLogger.write(App.class,
                    "Press Ctrl+C to stop...", AppsLogger.INFO);
        }
        Thread.currentThread().join();
    }
}
