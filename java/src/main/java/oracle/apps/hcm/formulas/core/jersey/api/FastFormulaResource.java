package oracle.apps.hcm.formulas.core.jersey.api;

import oracle.apps.fnd.applcore.log.AppsLogger;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//@Path("/11.13.18.05/fastFormulaAssistants")
@Path("/11.13.18.05/calculationEntries")
public class FastFormulaResource {

    /** Sentinel value for the built-in "Custom Formula" type in requests + dropdowns. */
    private static final String CUSTOM_TYPE = "Custom";

    private final AiService aiService = new AiService();
    private final ChatSessionStore sessionStore = ChatSessionStore.getInstance();
    private final DbiService dbiService = new DbiService();
    private final FormulaService formulaService = FormulaService.getInstance();
    private final FormulaTypesService formulaTypesService = new FormulaTypesService();
    private final SimulatorService simulatorService = new SimulatorService();
    private final TemplateService templateService = new TemplateService();
    private final ValidatorService validatorService = new ValidatorService();

    // ── Chat (POST streaming) ─────────────────────────────────────────────

    @POST
    @Path("/chat")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("text/event-stream")
    public Response chat(Map<String, Object> request) {
        String message = (String) request.get("message");
        // `editor_code`: the content currently in the Monaco editor. Renamed
        // from the former `code` field to avoid confusion with `template_code`.
        String editorCode = (String) request.getOrDefault("editor_code", "");
        String formulaType = (String) request.getOrDefault("formula_type", "TIME_LABOR");
        String sessionId = (String) request.get("session_id");

        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "POST /chat: formulaType=" + formulaType
                            + " session=" + sessionId
                            + " editorCodeLen=" + (editorCode == null ? 0 : editorCode.length())
                            + " messageLen=" + (message == null ? 0 : message.length()),
                    AppsLogger.INFO);
        }

        // `template_code`: when the frontend user picks a template from the
        // "Start with" dropdown we only receive its business key — a short
        // identifier like `ORA_FFT_CUSTOM_OVERTIME_PAY_CALCULATION_001` that
        // matches FF_FORMULA_TEMPLATES.TEMPLATE_CODE. The server then goes
        // back to the DB to fetch the CLOBs (FORMULA_TEXT + ADDITIONAL_PROMPT_TEXT),
        // so the HTTP payload stays small and the frontend can't get out of
        // sync with the authoritative template store.
        String templateCodeKey = (String) request.get("template_code");
        String templateBody = null;   // FORMULA_TEXT
        String templateRule = null;   // ADDITIONAL_PROMPT_TEXT
        if (templateCodeKey != null && !templateCodeKey.isBlank()) {
            try {
                Map<String, Object> template = templateService.findByTemplateCode(templateCodeKey);
                if (template != null) {
                    templateBody = (String) template.get("code");
                    templateRule = (String) template.get("rule");
                    if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                        AppsLogger.write(this,
                                "Resolved template_code=" + templateCodeKey
                                        + " body=" + (templateBody == null ? 0 : templateBody.length())
                                        + " rule=" + (templateRule == null ? 0 : templateRule.length()),
                                AppsLogger.FINER);
                    }
                } else if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                    AppsLogger.write(this,
                            "Chat request referenced unknown TEMPLATE_CODE: " + templateCodeKey,
                            AppsLogger.WARNING);
                }
            } catch (SQLException e) {
                // SEVERE in catch — DB lookup failure swallowed; the chat
                // path still continues so the user isn't blocked on a
                // template fetch, but ops needs to see the failure.
                AppsLogger.write(this, e, AppsLogger.SEVERE);
            }
        }

        // For the AiService contract: customSampleCode == "" means "Custom
        // type but no reference formula" (skips RAG — the Custom bucket is
        // general-purpose so off-topic RAG examples hurt more than help).
        // For non-Custom types with no reference, leave it null so RAG kicks in.
        String customSampleCode = templateBody;
        if (CUSTOM_TYPE.equalsIgnoreCase(formulaType)
                && (customSampleCode == null || customSampleCode.isBlank())) {
            customSampleCode = "";
        }

        sessionId = sessionStore.getOrCreateSession(sessionId);
        List<Map<String, String>> history = new ArrayList<>(sessionStore.getHistory(sessionId));

        final String sid = sessionId;
        final String csc = customSampleCode;
        final String cr = templateRule;

        StreamingOutput stream = new StreamingOutput() {
            public void write(OutputStream out) throws IOException {
                StringBuilder fullResponse = new StringBuilder();

                aiService.streamChat(message, editorCode, formulaType, history, csc, cr, new java.util.function.Consumer<String>() {
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

    // ── Chat (POST, blocking JSON) ─────────────────────────────────────────
    //
    // Non-streaming counterpart of /chat. Accepts the exact same request body
    // but blocks until the LLM returns the full response, then ships it back
    // as a single JSON object. For clients that would rather wait-and-parse
    // than deal with SSE framing.

    @POST
    @Path("/chat/sync")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response chatSync(Map<String, Object> request) {
        String message = (String) request.get("message");
        String editorCode = (String) request.getOrDefault("editor_code", "");
        String formulaType = (String) request.getOrDefault("formula_type", "TIME_LABOR");
        String sessionId = (String) request.get("session_id");

        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "POST /chat/sync: formulaType=" + formulaType
                            + " session=" + sessionId
                            + " editorCodeLen=" + (editorCode == null ? 0 : editorCode.length())
                            + " messageLen=" + (message == null ? 0 : message.length()),
                    AppsLogger.INFO);
        }

        // Template lookup — same logic as streaming /chat.
        String templateCodeKey = (String) request.get("template_code");
        String templateBody = null;
        String templateRule = null;
        if (templateCodeKey != null && !templateCodeKey.isBlank()) {
            try {
                Map<String, Object> template = templateService.findByTemplateCode(templateCodeKey);
                if (template != null) {
                    templateBody = (String) template.get("code");
                    templateRule = (String) template.get("rule");
                    if (AppsLogger.isEnabled(AppsLogger.FINER)) {
                        AppsLogger.write(this,
                                "chatSync: resolved template_code=" + templateCodeKey,
                                AppsLogger.FINER);
                    }
                } else if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                    AppsLogger.write(this,
                            "chatSync: unknown TEMPLATE_CODE " + templateCodeKey,
                            AppsLogger.WARNING);
                }
            } catch (SQLException e) {
                AppsLogger.write(this, e, AppsLogger.SEVERE);
            }
        }

        // Same customSampleCode semantics as /chat: "" for Custom-no-reference
        // (suppress RAG), null for other types (enable RAG).
        String customSampleCode = templateBody;
        if (CUSTOM_TYPE.equalsIgnoreCase(formulaType)
                && (customSampleCode == null || customSampleCode.isBlank())) {
            customSampleCode = "";
        }

        sessionId = sessionStore.getOrCreateSession(sessionId);
        List<Map<String, String>> history = new ArrayList<>(sessionStore.getHistory(sessionId));

        String rawResponse = aiService.chatOnce(
                message, editorCode, formulaType, history, customSampleCode, templateRule);

        // Mirror streaming's post-process: fix DEFAULT value types.
        String finalResponse = AiService.fixDefaultTypes(rawResponse);

        // Persist the turn so subsequent calls with the same session_id
        // see this exchange in their history.
        sessionStore.addTurn(sessionId, "user", message);
        sessionStore.addTurn(sessionId, "assistant", finalResponse);

        return Response.ok(Map.of(
                "text", finalResponse,
                "session_id", sessionId
        )).build();
    }

    // ── Complete (POST) ────────────────────────────────────────────────────

    @POST
    @Path("/complete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response complete(Map<String, Object> request) {
        String code = (String) request.getOrDefault("code", "");
        int cursorLine = ((Number) request.getOrDefault("cursor_line", 1)).intValue();

        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "POST /complete: cursorLine=" + cursorLine
                            + " codeLen=" + code.length(),
                    AppsLogger.INFO);
        }

        try {
            String suggestion = aiService.complete(code, cursorLine);
            return Response.ok(Map.of("suggestions", List.of(suggestion))).build();
        } catch (Exception e) {
            // SEVERE in catch — completion endpoint is best-effort but a
            // crashing provider should not poison the JSON response.
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            return Response.ok(Map.of("suggestions", List.of(""))).build();
        }
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
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "GET /dbi: module=" + module + " search=" + search
                            + " dataType=" + dataType + " limit=" + lim + " offset=" + off,
                    AppsLogger.INFO);
        }
        Map<String, Object> result = dbiService.getDbis(module, search, dataType, lim, off);
        return Response.ok(result).build();
    }

    @GET
    @Path("/dbi/modules")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDbiModules() {
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this, "GET /dbi/modules", AppsLogger.INFO);
        }
        return Response.ok(dbiService.getModules()).build();
    }

    // ── Debug (GET, DELETE) ────────────────────────────────────────────────

    @GET
    @Path("/debug/llm-logs")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDebugLogs() {
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this, "GET /debug/llm-logs", AppsLogger.FINER);
        }
        return Response.ok(LlmDebugLog.getInstance().getAll()).build();
    }

    @GET
    @Path("/debug/llm-logs/latest")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDebugLatest() {
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this, "GET /debug/llm-logs/latest", AppsLogger.FINER);
        }
        return Response.ok(LlmDebugLog.getInstance().getLatest()).build();
    }

    @DELETE
    @Path("/debug/llm-logs")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clearDebugLogs() {
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this, "DELETE /debug/llm-logs (clear)", AppsLogger.INFO);
        }
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
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "POST /explain: codeLen=" + code.length(), AppsLogger.INFO);
        }

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
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this, "GET /formulas", AppsLogger.INFO);
        }
        return Response.ok(formulaService.listAll()).build();
    }

    @POST
    @Path("/formulas")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createFormula(Map<String, Object> request) {
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "POST /formulas: name=" + request.get("name"), AppsLogger.INFO);
        }
        Map<String, Object> formula = formulaService.create(request);
        return Response.status(Response.Status.CREATED).entity(formula).build();
    }

    @GET
    @Path("/formulas/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFormula(@PathParam("id") String id) {
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this, "GET /formulas/" + id, AppsLogger.FINER);
        }
        Map<String, Object> formula = formulaService.findById(id);
        if (formula == null) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this, "Formula not found: " + id, AppsLogger.WARNING);
            }
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
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "PUT /formulas/" + id + " keys=" + updates.keySet(), AppsLogger.INFO);
        }
        Map<String, Object> formula = formulaService.update(id, updates);
        if (formula == null) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this, "PUT /formulas/" + id + " — not found", AppsLogger.WARNING);
            }
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
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this, "GET /formulas/" + id + "/export", AppsLogger.INFO);
        }
        Map<String, Object> formula = formulaService.findById(id);
        if (formula == null) {
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this, "Export: formula not found id=" + id, AppsLogger.WARNING);
            }
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

    // ── Formula Types (GET) ────────────────────────────────────────────────

    @GET
    @Path("/formula-types")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listFormulaTypes() {
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this, "GET /formula-types", AppsLogger.INFO);
        }
        return Response.ok(formulaTypesService.listAll()).build();
    }

    // ── Templates (GET, GET by id, POST, PUT, DELETE) ─────────────────────
    //
    // DB-backed CRUD for FF_FORMULA_TEMPLATES. The "formula_type" query param
    // accepts a formula type name (e.g. "Oracle Payroll"); the sentinel
    // "Custom" matches rows whose FORMULA_TYPE_ID is NULL. Omitting the param
    // returns every active template.

    @GET
    @Path("/templates")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listTemplates(@QueryParam("formula_type") String formulaType,
                                  @QueryParam("include_inactive") Boolean includeInactive) {
        boolean inactive = Boolean.TRUE.equals(includeInactive);
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "GET /templates: formulaType=" + formulaType
                            + " includeInactive=" + inactive,
                    AppsLogger.INFO);
        }
        try {
            return Response.ok(templateService.listByFormulaType(formulaType, inactive)).build();
        } catch (SQLException e) {
            // SEVERE in catch — DB unreachable for /templates is not normal
            // operation; surface it loudly so ops can act on it.
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            return templateDbError(e);
        }
    }

    @GET
    @Path("/templates/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTemplate(@PathParam("id") long id) {
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this, "GET /templates/" + id, AppsLogger.INFO);
        }
        try {
            Map<String, Object> row = templateService.findById(id);
            if (row == null) {
                if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                    AppsLogger.write(this, "Template not found id=" + id, AppsLogger.WARNING);
                }
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Template not found: " + id))
                        .build();
            }
            return Response.ok(row).build();
        } catch (SQLException e) {
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            return templateDbError(e);
        }
    }

    @POST
    @Path("/templates")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createTemplate(Map<String, Object> request) {
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "POST /templates: name=" + request.get("name")
                            + " template_code=" + request.get("template_code"),
                    AppsLogger.INFO);
        }
        try {
            Map<String, Object> created = templateService.create(request);
            if (AppsLogger.isEnabled(AppsLogger.INFO)) {
                AppsLogger.write(this,
                        "Created template_id=" + created.get("template_id"),
                        AppsLogger.INFO);
            }
            return Response.status(Response.Status.CREATED).entity(created).build();
        } catch (SQLException e) {
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            return templateDbError(e);
        }
    }

    @PUT
    @Path("/templates/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateTemplate(@PathParam("id") long id, Map<String, Object> updates) {
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "PUT /templates/" + id + " keys=" + updates.keySet(),
                    AppsLogger.INFO);
        }
        try {
            Map<String, Object> updated = templateService.update(id, updates);
            if (updated == null) {
                if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                    AppsLogger.write(this,
                            "PUT /templates/" + id + " — not found", AppsLogger.WARNING);
                }
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Template not found: " + id))
                        .build();
            }
            return Response.ok(updated).build();
        } catch (SQLException e) {
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            return templateDbError(e);
        }
    }

    @DELETE
    @Path("/templates/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteTemplate(@PathParam("id") long id) {
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this, "DELETE /templates/" + id, AppsLogger.INFO);
        }
        try {
            boolean removed = templateService.delete(id);
            if (!removed) {
                if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                    AppsLogger.write(this,
                            "DELETE /templates/" + id + " — not found", AppsLogger.WARNING);
                }
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Template not found: " + id))
                        .build();
            }
            return Response.ok(Map.of("status", "deleted", "template_id", id)).build();
        } catch (SQLException e) {
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            return templateDbError(e);
        }
    }

    private static Response templateDbError(SQLException e) {
        // Client sees a generic error; the SQLException is logged at SEVERE
        // by the caller's catch block.
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of(
                        "error", "Template store unavailable",
                        "detail", e.getMessage() == null ? "" : e.getMessage()))
                .build();
    }

    // ── Health (GET) ───────────────────────────────────────────────────────

    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        // Health is hit by load balancers — log at FINER so we don't drown
        // the application log in heartbeat noise.
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this, "GET /health", AppsLogger.FINER);
        }
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
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this, "POST /simulate rejected: code is empty", AppsLogger.WARNING);
            }
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "code is required"))
                    .build();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) request.getOrDefault("input_data", Map.of());
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "POST /simulate: codeLen=" + code.length()
                            + " inputKeys=" + inputs.keySet(),
                    AppsLogger.INFO);
        }
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
            if (AppsLogger.isEnabled(AppsLogger.WARNING)) {
                AppsLogger.write(this, "POST /validate rejected: code is empty", AppsLogger.WARNING);
            }
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "code is required"))
                    .build();
        }

        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "POST /validate: codeLen=" + code.length(), AppsLogger.INFO);
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
