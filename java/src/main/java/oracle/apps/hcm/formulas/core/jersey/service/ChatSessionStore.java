package oracle.apps.hcm.formulas.core.jersey.service;

import oracle.apps.fnd.applcore.log.AppsLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory chat session store.
 * Mirrors Python's ChatSession model — stores conversation history by session ID.
 */
public class ChatSessionStore {

    private static final ChatSessionStore INSTANCE = new ChatSessionStore();

    public static ChatSessionStore getInstance() { return INSTANCE; }

    private final Map<String, List<Map<String, String>>> sessions = new ConcurrentHashMap<>();

    public String getOrCreateSession(String sessionId) {
        boolean fresh = sessionId == null || sessionId.isBlank();
        if (fresh) {
            sessionId = UUID.randomUUID().toString();
        }
        boolean created = sessions.putIfAbsent(sessionId, new ArrayList<>()) == null;
        if (created && AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "New chat session: " + sessionId + (fresh ? " (auto-allocated)" : ""),
                    AppsLogger.INFO);
        }
        return sessionId;
    }

    public List<Map<String, String>> getHistory(String sessionId) {
        return sessions.getOrDefault(sessionId, List.of());
    }

    public void addTurn(String sessionId, String role, String content) {
        if (AppsLogger.isEnabled(AppsLogger.FINER)) {
            AppsLogger.write(this,
                    "addTurn: session=" + sessionId + " role=" + role
                            + " contentLen=" + (content == null ? 0 : content.length()),
                    AppsLogger.FINER);
        }
        sessions.computeIfAbsent(sessionId, k -> new ArrayList<>())
                .add(Map.of("role", role, "content", content));
    }
}
