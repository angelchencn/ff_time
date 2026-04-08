package oracle.apps.hcm.formulas.core.jersey.api;

import oracle.apps.hcm.formulas.core.jersey.service.*;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

//@Path("/11.13.18.05/fastFormulaAssistants")
@Path("/11.13.18.05/calculationEntries")
public class FastFormulaResource {

    private static final Logger LOG = Logger.getLogger(FastFormulaResource.class.getName());

    private final AiService aiService = new AiService();
    private final ChatSessionStore sessionStore = ChatSessionStore.getInstance();
    private final CustomFormulaService customService = CustomFormulaService.getInstance();
    private final DbiService dbiService = new DbiService();
    private final FormulaService formulaService = FormulaService.getInstance();
    private final FormulaTypesService formulaTypesService = new FormulaTypesService();
    private final SimulatorService simulatorService = new SimulatorService();
    private final ValidatorService validatorService = new ValidatorService();

    // ── Chat (POST streaming) ─────────────────────────────────────────────

    @POST
    @Path("/chat")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("text/event-stream")
    public Response chat(Map<String, Object> request) {
        String message = (String) request.get("message");
        String code = (String) request.getOrDefault("code", "");
        String formulaType = (String) request.getOrDefault("formula_type", "TIME_LABOR");
        String sessionId = (String) request.get("session_id");

        // Custom type: use selected sample as context, skip RAG
        String customSampleCode = null;
        String customRule = null;
        if (customService.isCustomType(formulaType)) {
            String selectedSample = (String) request.get("selected_sample");
            if (selectedSample != null && !selectedSample.isBlank()) {
                Map<String, Object> sample = customService.findSample(selectedSample);
                if (sample != null) {
                    customSampleCode = (String) sample.get("code");
                    customRule = (String) sample.get("rule");
                }
            }
            if (customSampleCode == null) {
                customSampleCode = "";
            }
        }

        sessionId = sessionStore.getOrCreateSession(sessionId);
        List<Map<String, String>> history = new ArrayList<>(sessionStore.getHistory(sessionId));

        final String sid = sessionId;
        final String csc = customSampleCode;
        final String cr = customRule;

        StreamingOutput stream = new StreamingOutput() {
            public void write(OutputStream out) throws IOException {
                StringBuilder fullResponse = new StringBuilder();

                aiService.streamChat(message, code, formulaType, history, csc, cr, new java.util.function.Consumer<String>() {
                    public void accept(String token) {
                        fullResponse.append(token);
                        try {
                            String sseData = "data: {\"text\":\"" + escapeJson(token) + "\"}\n\n";
                            out.write(sseData.getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        } catch (IOException e) {
                            // client disconnected
                        }
                    }
                });

                // Post-process: fix DEFAULT value types
                String response = fullResponse.toString();
                String fixed = AiService.fixDefaultTypes(response);
                if (!fixed.equals(response)) {
                    try {
                        String replaceData = "data: {\"replace\":\"" + escapeJson(fixed) + "\"}\n\n";
                        out.write(replaceData.getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    } catch (IOException e) {
                        // client disconnected
                    }
                    response = fixed;
                }

                sessionStore.addTurn(sid, "user", message);
                sessionStore.addTurn(sid, "assistant", response);

                // Send done signal
                try {
                    out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (IOException e) {
                    // client disconnected
                }
            }
        };

        return Response.ok(stream, "text/event-stream")
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("X-Accel-Buffering", "no")
                .build();
    }

    // ── Complete (POST) ────────────────────────────────────────────────────

    @POST
    @Path("/complete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response complete(Map<String, Object> request) {
        String code = (String) request.getOrDefault("code", "");
        int cursorLine = ((Number) request.getOrDefault("cursor_line", 1)).intValue();

        String suggestion = aiService.complete(code, cursorLine);
        return Response.ok(Map.of("suggestions", List.of(suggestion))).build();
    }

    // ── Custom Formulas (GET, GET by name, PUT) ────────────────────────────

    @GET
    @Path("/custom-formulas")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listCustomSamples() {
        return Response.ok(customService.getAllSamples()).build();
    }

    @GET
    @Path("/custom-formulas/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCustomSample(@PathParam("name") String name) {
        Map<String, Object> sample = customService.findSample(name);
        if (sample == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Sample not found: " + name))
                    .build();
        }
        return Response.ok(sample).build();
    }

    @PUT
    @Path("/custom-formulas")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateCustomSamples(List<Map<String, Object>> samples) {
        customService.replaceSamples(samples);
        return Response.ok(Map.of("status", "saved", "count", samples.size())).build();
    }

    // ── DBI (GET with query params) ────────────────────────────────────────

    @GET
    @Path("/dbi")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDbis(@QueryParam("module") String module,
                            @QueryParam("search") String search,
                            @QueryParam("data_type") String dataType,
                            @QueryParam("limit") Integer limit,
                            @QueryParam("offset") Integer offset) {
        int lim = limit != null ? limit : 500;
        int off = offset != null ? offset : 0;
        Map<String, Object> result = dbiService.getDbis(module, search, dataType, lim, off);
        return Response.ok(result).build();
    }

    @GET
    @Path("/dbi/modules")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDbiModules() {
        return Response.ok(dbiService.getModules()).build();
    }

    // ── Debug (GET, DELETE) ────────────────────────────────────────────────

    @GET
    @Path("/debug/llm-logs")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDebugLogs() {
        return Response.ok(LlmDebugLog.getInstance().getAll()).build();
    }

    @GET
    @Path("/debug/llm-logs/latest")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDebugLatest() {
        return Response.ok(LlmDebugLog.getInstance().getLatest()).build();
    }

    @DELETE
    @Path("/debug/llm-logs")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clearDebugLogs() {
        LlmDebugLog.getInstance().clear();
        return Response.ok(Map.of("status", "cleared")).build();
    }

    // ── Explain (POST streaming) ──────────────────────────────────────────

    @POST
    @Path("/explain")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("text/event-stream")
    public Response explain(Map<String, Object> request) {
        String code = (String) request.getOrDefault("code", "");

        StreamingOutput stream = new StreamingOutput() {
            public void write(OutputStream out) throws IOException {
                aiService.streamExplain(code, new java.util.function.Consumer<String>() {
                    public void accept(String token) {
                        try {
                            String sseData = "data: {\"text\":\"" + escapeJson(token) + "\"}\n\n";
                            out.write(sseData.getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        } catch (IOException e) {
                            // client disconnected
                        }
                    }
                });

                try {
                    out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (IOException e) {
                    // client disconnected
                }
            }
        };

        return Response.ok(stream, "text/event-stream")
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("X-Accel-Buffering", "no")
                .build();
    }

    // ── Formulas (GET, POST, PUT, export) ──────────────────────────────────

    @GET
    @Path("/formulas")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listFormulas() {
        return Response.ok(formulaService.listAll()).build();
    }

    @POST
    @Path("/formulas")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createFormula(Map<String, Object> request) {
        Map<String, Object> formula = formulaService.create(request);
        return Response.status(Response.Status.CREATED).entity(formula).build();
    }

    @GET
    @Path("/formulas/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFormula(@PathParam("id") String id) {
        Map<String, Object> formula = formulaService.findById(id);
        if (formula == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Formula not found"))
                    .build();
        }
        return Response.ok(formula).build();
    }

    @PUT
    @Path("/formulas/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateFormula(@PathParam("id") String id, Map<String, Object> updates) {
        Map<String, Object> formula = formulaService.update(id, updates);
        if (formula == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Formula not found"))
                    .build();
        }
        return Response.ok(formula).build();
    }

    @GET
    @Path("/formulas/{id}/export")
    @Produces(MediaType.APPLICATION_JSON)
    public Response exportFormula(@PathParam("id") String id) {
        Map<String, Object> formula = formulaService.findById(id);
        if (formula == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Formula not found"))
                    .build();
        }
        return Response.ok(Map.of(
                "id", formula.getOrDefault("id", ""),
                "name", formula.getOrDefault("name", ""),
                "code", formula.getOrDefault("code", ""),
                "content", formula.getOrDefault("code", "")
        )).build();
    }

    // ── Formula Types (GET, GET template) ──────────────────────────────────

    @GET
    @Path("/formula-types")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listFormulaTypes() {
        return Response.ok(formulaTypesService.listAll()).build();
    }

    @GET
    @Path("/formula-types/{typeName}/template")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFormulaTypeTemplate(@PathParam("typeName") String typeName) {
        Map<String, Object> template = formulaTypesService.getTemplate(typeName);
        if (template == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Unknown formula type: " + typeName))
                    .build();
        }
        return Response.ok(template).build();
    }

    // ── Health (GET) ───────────────────────────────────────────────────────

    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        return Response.ok(Map.of("status", "healthy")).build();
    }

    // ── Simulate (POST) ────────────────────────────────────────────────────

    @POST
    @Path("/simulate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response simulate(Map<String, Object> request) {
        String code = (String) request.get("code");
        if (code == null || code.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "code is required"))
                    .build();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) request.getOrDefault("input_data", Map.of());
        Map<String, Object> result = simulatorService.simulate(code, inputs);
        return Response.ok(result).build();
    }

    // ── Validate (POST) ────────────────────────────────────────────────────

    @POST
    @Path("/validate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response validate(Map<String, String> request) {
        String code = request.get("code");
        if (code == null || code.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "code is required"))
                    .build();
        }

        Map<String, Object> result = validatorService.validate(code);
        return Response.ok(result).build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
