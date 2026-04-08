package oracle.apps.hcm.formulas.core.jerseyTest;

import oracle.apps.hcm.formulas.core.jersey.api.*;
import oracle.apps.hcm.formulas.core.jersey.config.*;
import oracle.apps.hcm.formulas.core.jersey.model.*;
import oracle.apps.hcm.formulas.core.jersey.parser.*;
import oracle.apps.hcm.formulas.core.jersey.parser.AstNodes.*;
import oracle.apps.hcm.formulas.core.jersey.parser.AstNodes;
import oracle.apps.hcm.formulas.core.jersey.service.*;

import javax.ws.rs.core.Response;
import org.junit.Test;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class HealthResourceTest {

    private final FastFormulaResource resource = new FastFormulaResource();

    @Test
    public void healthEndpointReturnsHealthy() {
        Response response = resource.health();
        assertEquals(200, response.getStatus());

        @SuppressWarnings("unchecked")
        var body = (Map<String, String>) response.getEntity();
        assertEquals("healthy", body.get("status"));
    }
}
