/** Optimistic stage command; stale or duplicate requests are rejected. */
public final class AdvanceGameCommand {
    private final String sessionId;
    private final GameStage expectedStage;

    public AdvanceGameCommand(String sessionId, GameStage expectedStage) {
        this.sessionId = sessionId;
        this.expectedStage = expectedStage;
    }

    public String getSessionId() { return sessionId; }
    public GameStage getExpectedStage() { return expectedStage; }
}
