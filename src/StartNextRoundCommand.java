public final class StartNextRoundCommand {
    private final String sessionId;

    public StartNextRoundCommand(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() { return sessionId; }
}
