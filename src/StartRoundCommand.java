/** Starts the first authoritative round after players have joined the lobby. */
public final class StartRoundCommand {
    private final String sessionId;
    private final double startingMoney;
    private final double entryBet;

    public StartRoundCommand(String sessionId, double startingMoney, double entryBet) {
        this.sessionId = sessionId;
        this.startingMoney = startingMoney;
        this.entryBet = entryBet;
    }

    public String getSessionId() { return sessionId; }
    public double getStartingMoney() { return startingMoney; }
    public double getEntryBet() { return entryBet; }
}
