/** Immutable identity assigned when a player joins a session lobby. */
public final class PlayerRegistration {
    private final String sessionId;
    private final int playerId;
    private final String displayName;

    PlayerRegistration(String sessionId, int playerId, String displayName) {
        this.sessionId = sessionId;
        this.playerId = playerId;
        this.displayName = displayName;
    }

    public String getSessionId() { return sessionId; }
    public int getPlayerId() { return playerId; }
    public String getDisplayName() { return displayName; }
}
