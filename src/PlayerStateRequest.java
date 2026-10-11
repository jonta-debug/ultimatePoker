/** Requests private state only for the requesting player's own stable ID. */
public final class PlayerStateRequest {
    private final String sessionId;
    private final int requestingPlayerId;

    public PlayerStateRequest(String sessionId, int requestingPlayerId) {
        this.sessionId = sessionId;
        this.requestingPlayerId = requestingPlayerId;
    }

    public String getSessionId() { return sessionId; }
    public int getRequestingPlayerId() { return requestingPlayerId; }
}
