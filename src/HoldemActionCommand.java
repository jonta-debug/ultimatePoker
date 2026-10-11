public final class HoldemActionCommand {
    private final String sessionId;
    private final int playerId;
    private final HoldemActionType action;
    private final long amount;

    public HoldemActionCommand(String sessionId, int playerId,
                               HoldemActionType action, long amount) {
        this.sessionId = sessionId;
        this.playerId = playerId;
        this.action = action;
        this.amount = amount;
    }
    public String getSessionId() { return sessionId; }
    public int getPlayerId() { return playerId; }
    public HoldemActionType getAction() { return action; }
    public long getAmount() { return amount; }
}
