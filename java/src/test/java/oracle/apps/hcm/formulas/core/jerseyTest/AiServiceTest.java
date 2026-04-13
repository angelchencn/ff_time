package oracle.apps.hcm.formulas.core.jerseyTest;

import oracle.apps.hcm.formulas.core.jersey.api.*;
import oracle.apps.hcm.formulas.core.jersey.config.*;
import oracle.apps.hcm.formulas.core.jersey.model.*;
import oracle.apps.hcm.formulas.core.jersey.parser.*;
import oracle.apps.hcm.formulas.core.jersey.parser.AstNodes.*;
import oracle.apps.hcm.formulas.core.jersey.parser.AstNodes;
import oracle.apps.hcm.formulas.core.jersey.service.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.Assert.*;

/**
 * Mirrors test_session_fixes.py TestFixDefaultTypes (9 tests)
 * + test_ai_service.py system prompt check (1 test)
 * + provider selection tests (4 tests).
 */
public class AiServiceTest {

    @Test
    public void systemPromptContainsKeywords() {
        assertTrue(AiService.DEFAULT_SYSTEM_PROMPT.contains("Fast Formula"));
        assertTrue(AiService.DEFAULT_SYSTEM_PROMPT.contains("DEFAULT"));
    }

    @Test
    public void systemPromptContainsFusionCloudDeclaration() {
        assertTrue(AiService.DEFAULT_SYSTEM_PROMPT.contains("Fusion Cloud"));
        assertTrue(AiService.DEFAULT_SYSTEM_PROMPT.contains("Do NOT use EBS"));
    }

    @Test
    public void systemPromptContainsFormulaTypeFirst() {
        assertTrue(AiService.DEFAULT_SYSTEM_PROMPT.contains("Formula Type First"));
        assertTrue(AiService.DEFAULT_SYSTEM_PROMPT.contains("formula_status"));
        assertTrue(AiService.DEFAULT_SYSTEM_PROMPT.contains("skip_flag"));
    }

    @Test
    public void systemPromptContainsAntiHallucination() {
        assertTrue(AiService.DEFAULT_SYSTEM_PROMPT.contains("Anti-Hallucination"));
        assertTrue(AiService.DEFAULT_SYSTEM_PROMPT.contains("PLACEHOLDER"));
    }

    @Test
    public void systemPromptContainsCompileSelfCheck() {
        assertTrue(AiService.DEFAULT_SYSTEM_PROMPT.contains("Compile Self-Check"));
        assertTrue(AiService.DEFAULT_SYSTEM_PROMPT.contains("READ-ONLY"));
    }

    // ── Provider selection tests (flattened from @Nested ProviderSelectionTests) ──

    @Test
    public void providerSelection_injectCustomProvider() {
        List<String> received = new ArrayList<>();
        LlmProvider mock = new LlmProvider() {
            public void streamChat(List<Map<String, String>> messages, int maxTokens,
                                   Consumer<String> cb) { cb.accept("mock-response"); }
            public String complete(List<Map<String, String>> messages, int maxTokens) { return "mock-complete"; }
            public boolean isAvailable() { return true; }
            public String name() { return "MockProvider"; }
        };

        AiService service = new AiService(mock);
        service.streamChat("test", "", "Oracle Payroll", received::add);
        assertFalse(received.isEmpty());
        assertEquals("mock-response", received.get(0));
    }

    @Test
    public void providerSelection_injectCustomProviderComplete() {
        LlmProvider mock = new LlmProvider() {
            public void streamChat(List<Map<String, String>> messages, int maxTokens,
                                   Consumer<String> cb) { cb.accept("x"); }
            public String complete(List<Map<String, String>> messages, int maxTokens) { return "completed-code"; }
            public boolean isAvailable() { return true; }
            public String name() { return "MockProvider"; }
        };

        AiService service = new AiService(mock);
        String result = service.complete("x = 1", 1);
        assertEquals("completed-code", result);
    }

    @Test
    public void providerSelection_unavailableProviderReturnsError() {
        List<String> received = new ArrayList<>();
        LlmProvider unavailable = new LlmProvider() {
            public void streamChat(List<Map<String, String>> messages, int maxTokens,
                                   Consumer<String> cb) { cb.accept("should-not-reach"); }
            public String complete(List<Map<String, String>> messages, int maxTokens) { return "nope"; }
            public boolean isAvailable() { return false; }
            public String name() { return "Unavailable"; }
        };

        AiService service = new AiService(unavailable);
        service.streamChat("test", "", "Oracle Payroll", received::add);
        assertEquals(1, received.size());
        assertTrue(received.get(0).contains("Error"));
    }

    @Test
    public void providerSelection_unavailableProviderCompleteReturnsEmpty() {
        LlmProvider unavailable = new LlmProvider() {
            public void streamChat(List<Map<String, String>> messages, int maxTokens,
                                   Consumer<String> cb) {}
            public String complete(List<Map<String, String>> messages, int maxTokens) { return "nope"; }
            public boolean isAvailable() { return false; }
            public String name() { return "Unavailable"; }
        };

        AiService service = new AiService(unavailable);
        assertEquals("", service.complete("x = 1", 1));
    }

    // ── Fix default types tests (flattened from @Nested FixDefaultTypesTests) ──

    @Test
    public void fixDefaultTypes_nameVariableGetsStringDefault() {
        assertEquals("DEFAULT FOR BASE_TASK_NAME IS ' '",
                AiService.fixDefaultTypes("DEFAULT FOR BASE_TASK_NAME IS 0"));
    }

    @Test
    public void fixDefaultTypes_counterVariableStaysNumeric() {
        assertEquals("DEFAULT FOR REPEAT_COUNTER IS 0",
                AiService.fixDefaultTypes("DEFAULT FOR REPEAT_COUNTER IS 0"));
    }

    @Test
    public void fixDefaultTypes_dateVariableGetsDateDefault() {
        assertEquals("DEFAULT FOR EFFECTIVE_DATE IS '01-JAN-0001'(DATE)",
                AiService.fixDefaultTypes("DEFAULT FOR EFFECTIVE_DATE IS 0"));
    }

    @Test
    public void fixDefaultTypes_statusVariableGetsStringDefault() {
        assertEquals("DEFAULT FOR ROLLBACK_STATUS IS ' '",
                AiService.fixDefaultTypes("DEFAULT FOR ROLLBACK_STATUS IS 0"));
    }

    @Test
    public void fixDefaultTypes_typeVariableGetsStringDefault() {
        assertEquals("DEFAULT FOR PROCESS_TYPE IS ' '",
                AiService.fixDefaultTypes("DEFAULT FOR PROCESS_TYPE IS 0"));
    }

    @Test
    public void fixDefaultTypes_alreadyCorrectStringNotChanged() {
        assertEquals("DEFAULT FOR BASE_TASK_NAME IS ' '",
                AiService.fixDefaultTypes("DEFAULT FOR BASE_TASK_NAME IS ' '"));
    }

    @Test
    public void fixDefaultTypes_alreadyCorrectNumberNotChanged() {
        assertEquals("DEFAULT FOR HOURS_WORKED IS 0",
                AiService.fixDefaultTypes("DEFAULT FOR HOURS_WORKED IS 0"));
    }

    @Test
    public void fixDefaultTypes_multiline() {
        String code = "DEFAULT FOR BASE_TASK_NAME IS 0\n"
                + "DEFAULT FOR REPEAT_COUNTER IS 0\n"
                + "DEFAULT FOR EFFECTIVE_DATE IS 0\n"
                + "DEFAULT FOR PROCESS_TYPE IS 0";
        String fixed = AiService.fixDefaultTypes(code);
        String[] lines = fixed.split("\n");
        assertTrue(lines[0].contains("IS ' '"));         // NAME -> string
        assertTrue(lines[1].contains("IS 0"));            // COUNTER -> number
        assertTrue(lines[2].contains("(DATE)"));          // DATE -> date
        assertTrue(lines[3].contains("IS ' '"));          // TYPE -> string
    }

    @Test
    public void fixDefaultTypes_idVariableStaysNumeric() {
        assertEquals("DEFAULT FOR PAYROLL_RUN_ID IS 0",
                AiService.fixDefaultTypes("DEFAULT FOR PAYROLL_RUN_ID IS 0"));
    }
}
