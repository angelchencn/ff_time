package oracle.apps.hcm.formulas.core.jersey.service;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;

public class AiServiceAsyncDebugTest {

    @Test
    public void asyncSubmitRecordsPromptContextToDebugLog() {
        FakeProvider provider = new FakeProvider();
        FakeDebugRecorder recorder = new FakeDebugRecorder();
        AiService service = new AiService(provider, recorder);

        String submitResponse = service.submitAsync(
                "generate formula", "RETURN x", "Custom",
                List.of(), "", "", "GPT5MINI", "WF");

        assertEquals("{\"jobId\":\"job-1\"}", submitResponse);
        assertEquals(1, recorder.recordCalls);
        assertEquals("submitAsync", recorder.endpoint);
        assertEquals("generate formula", recorder.context.messageOrEmpty());
        assertEquals("RETURN x", recorder.context.editorCodeOrEmpty());
        assertEquals("", recorder.response);
    }

    @Test
    public void asyncSubmitResultIncludesDebugLogId() {
        FakeProvider provider = new FakeProvider();
        FakeDebugRecorder recorder = new FakeDebugRecorder();
        AiService service = new AiService(provider, recorder);

        AiService.AsyncSubmitResult result = service.submitAsyncWithDebugLog(
                "generate formula", "RETURN x", "Custom",
                List.of(), "", "", "GPT5MINI", "WF", true);

        assertEquals("{\"jobId\":\"job-1\"}", result.response());
        assertEquals(123L, result.logId());
    }

    @Test
    public void asyncSubmitCanSuppressSystemPromptInDebugLog() {
        FakeProvider provider = new FakeProvider();
        FakeDebugRecorder recorder = new FakeDebugRecorder();
        AiService service = new PromptedAiService(provider, recorder);

        service.submitAsync(
                "generate formula", "RETURN x", "Custom",
                List.of(), "", "", "GPT5MINI", "WF", false);

        assertEquals("", recorder.context.systemPromptOrEmpty());
    }

    private static class FakeProvider implements LlmProvider {
        public void streamChat(List<Map<String, String>> messages, int maxTokens,
                               Consumer<String> tokenCallback) {
        }

        public String complete(List<Map<String, String>> messages, int maxTokens) {
            return "";
        }

        public boolean isAvailable() {
            return true;
        }

        public String name() {
            return "FakeProvider";
        }

        public String submitAsync(PromptContext context) {
            return "{\"jobId\":\"job-1\"}";
        }
    }

    private static class PromptedAiService extends AiService {
        PromptedAiService(LlmProvider provider, DebugRecorder recorder) {
            super(provider, recorder);
        }

        @Override
        public String getSystemPrompt() {
            return "DB system prompt";
        }
    }

    private static class FakeDebugRecorder implements AiService.DebugRecorder {
        int recordCalls;
        String endpoint;
        PromptContext context;
        String response;

        public long record(String model, int maxTokens, String endpoint,
                           PromptContext context, String response) {
            this.recordCalls++;
            this.endpoint = endpoint;
            this.context = context;
            this.response = response;
            return 123L;
        }
    }
}
