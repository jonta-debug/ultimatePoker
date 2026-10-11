/** Data-only request to reserve a stable player ID in a session. */
public final class JoinGameRequest {
    private final String sessionId;
    private final String displayName;

    public JoinGameRequest(String sessionId, String displayName) {
        this.sessionId = sessionId;
        this.displayName = displayName;
    }

    public String getSessionId() { return sessionId; }
    public String getDisplayName() { return displayName; }
}
