package oracle.apps.hcm.formulas.core.jersey.config;

import oracle.apps.fnd.applcore.log.AppsLogger;

import javax.ws.rs.ApplicationPath;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

@ApplicationPath("/api")
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(JerseyConfig.class,
                    "Registering Jersey packages, JacksonFeature, CorsFilter", AppsLogger.INFO);
        }

        packages("oracle.apps.hcm.formulas.core.jersey.api",
                 "oracle.apps.hcm.formulas.core.jersey.config");

        // JSON support
        register(JacksonFeature.class);

        // CORS filter
        register(CorsFilter.class);

        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(JerseyConfig.class,
                    "JerseyConfig initialization complete", AppsLogger.FINER);
        }
    }
}
