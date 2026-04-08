package oracle.apps.hcm.formulas.core.jersey.config;

import javax.ws.rs.ApplicationPath;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

@ApplicationPath("/api")
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
        packages("oracle.apps.hcm.formulas.core.jersey.api",
                 "oracle.apps.hcm.formulas.core.jersey.config");

        // JSON support
        register(JacksonFeature.class);

        // CORS filter
        register(CorsFilter.class);
    }
}
