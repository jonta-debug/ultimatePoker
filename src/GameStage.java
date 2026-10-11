/** Authoritative stages of an Ultimate Poker round. */
public enum GameStage {
    WAITING_FOR_PLAYERS("Waiting for players", 0, 0, 0, false),
    PREFLOP("Preflop", 0, 0, 1, false),
    FLOP_DEALT("Flop", 3, 0, 1, false),
    FLOP("Flop", 3, 3, 1, false),
    TURN_DEALT("Turn", 4, 3, 1, false),
    TURN("Turn", 4, 4, 1, false),
    RIVER_DEALT("River card hidden", 5, 4, 1, false),
    PLAYER_CARDS_REVEALED("Player reveal", 5, 4, 2, false),
    RIVER("River", 5, 5, 2, false),
    SHOWDOWN("Showdown", 5, 5, 2, true);

    private final String statusText;
    private final int dealtCommunityCardCount;
    private final int visibleCommunityCardCount;
    private final int publicHoleCardCount;
    private final boolean showdown;

    GameStage(String statusText, int dealtCommunityCardCount,
              int visibleCommunityCardCount, int publicHoleCardCount,
              boolean showdown) {
        this.statusText = statusText;
        this.dealtCommunityCardCount = dealtCommunityCardCount;
        this.visibleCommunityCardCount = visibleCommunityCardCount;
        this.publicHoleCardCount = publicHoleCardCount;
        this.showdown = showdown;
    }

    public String getStatusText() { return statusText; }
    public int getDealtCommunityCardCount() { return dealtCommunityCardCount; }
    public int getVisibleCommunityCardCount() { return visibleCommunityCardCount; }
    public int getPublicHoleCardCount() { return publicHoleCardCount; }
    public boolean isShowdown() { return showdown; }
    public boolean isRoundActive() { return this != WAITING_FOR_PLAYERS && this != SHOWDOWN; }
}
