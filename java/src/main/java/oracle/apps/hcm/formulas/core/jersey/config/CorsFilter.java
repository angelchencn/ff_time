package oracle.apps.hcm.formulas.core.jersey.config;

import oracle.apps.fnd.applcore.log.AppsLogger;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class CorsFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {
        // Per-request tracing at FINER only — don't spam the app log on every
        // hit, but give ops a knob to flip on when diagnosing CORS issues.
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(CorsFilter.class,
                    "CORS filter: " + requestContext.getMethod() + " "
                            + requestContext.getUriInfo().getPath()
                            + " → status " + responseContext.getStatus(),
                    AppsLogger.FINER);
        }

        responseContext.getHeaders().add("Access-Control-Allow-Origin", "*");
        responseContext.getHeaders().add("Access-Control-Allow-Headers",
                "origin, content-type, accept, authorization");
        responseContext.getHeaders().add("Access-Control-Allow-Methods",
                "GET, POST, PUT, DELETE, OPTIONS, HEAD");
    }
}
