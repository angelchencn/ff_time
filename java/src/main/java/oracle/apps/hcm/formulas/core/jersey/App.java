package oracle.apps.hcm.formulas.core.jersey;

import oracle.apps.hcm.formulas.core.jersey.config.*;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;

import java.net.URI;
import java.util.logging.Logger;

public class App {

    private static final Logger LOG = Logger.getLogger(App.class.getName());
    private static final String BASE_URI = "http://0.0.0.0:8000/api/";

    public static void main(String[] args) throws Exception {
        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(
                URI.create(BASE_URI), new JerseyConfig());

        LOG.info("FF Time server started at " + BASE_URI + "api/");
        LOG.info("Press Ctrl+C to stop...");
        Thread.currentThread().join();
    }
}
