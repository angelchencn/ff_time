package oracle.apps.hcm.formulas.core.jersey.service;

import oracle.apps.fnd.applcore.log.AppsLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory chat session store.
 *
 * <p>Holds two pieces of state per chat session:</p>
 * <ul>
 *   <li><b>history</b> — the {@code user}/{@code assistant} message log
 *       that is replayed back to the LLM on every turn (LLM APIs are
 *       stateless, so we re-send the whole history each call).</li>
 *   <li><b>customRule</b> — the {@code ADDITIONAL_PROMPT_TEXT} from the
 *       template the user picked when they started the conversation.
 *       This is an instruction-style rule (e.g. "always use 24-hour
 *       format", "never round below 0.25") that must apply to <em>every</em>
 *       turn, not just the first. The frontend only ships
 *       {@code template_code} on the first call (the dropdown clears
 *       after each send), so the backend persists the resolved rule
 *       against the session id and pulls it out on every subsequent
 *       turn.</li>
 * </ul>
 *
 * <p>Both maps are in-memory; they live for the lifetime of the JVM and
 * are wiped on restart. Cleanup of stale sessions is a separate concern
 * not addressed here.</p>
 */
public class ChatSessionStore {

    private static final ChatSessionStore INSTANCE = new ChatSessionStore();

    public static ChatSessionStore getInstance() { return INSTANCE; }

    private final Map<String, List<Map<String, String>>> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> customRules = new ConcurrentHashMap<>();

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

    /**
     * Persist the {@code ADDITIONAL_PROMPT_TEXT} (custom rule) for this
     * session. Called when a chat request arrives carrying a non-blank
     * rule — typically the first turn after the user picks a template,
     * or whenever the user picks a different template mid-conversation.
     *
     * <p>Null / blank rules are silently ignored so this method is safe
     * to call from request handlers without pre-checking.</p>
     */
    public void setCustomRule(String sessionId, String rule) {
        if (rule == null || rule.isBlank()) {
            return;
        }
        customRules.put(sessionId, rule);
        if (AppsLogger.isEnabled(AppsLogger.INFO)) {
            AppsLogger.write(this,
                    "Stored customRule for session=" + sessionId
                            + " (" + rule.length() + " chars)",
                    AppsLogger.INFO);
        }
    }

    /**
     * Retrieve the persisted custom rule for this session, or {@code null}
     * if none was ever set. The chat path injects this into the system
     * prompt on <em>every</em> turn so an instruction-style rule from the
     * starting template applies to the whole conversation.
     */
    public String getCustomRule(String sessionId) {
        return customRules.get(sessionId);
    }
}
