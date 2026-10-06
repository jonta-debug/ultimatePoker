/** Presentation stages owned by PokerTableController. */
public enum RevealStage {
    PREFLOP_ONE_CARD_VISIBLE("Preflop", 0, 0, 1, false, false),
    FLOP_DEAL_FACE_DOWN("Flop", 3, 0, 1, false, false),
    FLOP_REVEAL("Flop", 3, 3, 1, false, false),
    TURN_DEAL_FACE_DOWN("Turn", 4, 3, 1, false, false),
    TURN_REVEAL("Turn", 4, 4, 1, false, false),
    RIVER_DEAL_FACE_DOWN("River card hidden", 5, 4, 1, false, false),
    PLAYER_SECOND_CARD_REVEAL("Player reveal", 5, 4, 2, false, false),
    RIVER_REVEAL("River", 5, 5, 2, false, false),
    SHOWDOWN("Showdown", 5, 5, 2, true, true);

    private final String statusText;
    private final int dealtCommunityCardCount;
    private final int revealedCommunityCardCount;
    private final int visiblePlayerCardCount;
    private final boolean showWinner;
    private final boolean showFinalHandStatus;

    RevealStage(String statusText, int dealtCommunityCardCount,
                int revealedCommunityCardCount, int visiblePlayerCardCount,
                boolean showWinner, boolean showFinalHandStatus) {
        this.statusText = statusText;
        this.dealtCommunityCardCount = dealtCommunityCardCount;
        this.revealedCommunityCardCount = revealedCommunityCardCount;
        this.visiblePlayerCardCount = visiblePlayerCardCount;
        this.showWinner = showWinner;
        this.showFinalHandStatus = showFinalHandStatus;
    }

    public String getStatusText() {
        return statusText;
    }

    public int getDealtCommunityCardCount() {
        return dealtCommunityCardCount;
    }

    public int getRevealedCommunityCardCount() {
        return revealedCommunityCardCount;
    }

    public int getVisibleCommunityCardCount() {
        return revealedCommunityCardCount;
    }

    public int getFaceDownCommunityCardCount() {
        return dealtCommunityCardCount - revealedCommunityCardCount;
    }

    public int getVisiblePlayerCardCount() {
        return visiblePlayerCardCount;
    }

    public boolean shouldShowWinner() {
        return showWinner;
    }

    public boolean shouldShowFinalHandStatus() {
        return showFinalHandStatus;
    }

    public boolean isShowdown() {
        return this == SHOWDOWN;
    }

    public boolean isTerminal() {
        return this == SHOWDOWN;
    }

    public boolean hasNext() {
        return !isTerminal();
    }

    public RevealStage next() {
        if (isTerminal()) {
            return this;
        }
        return values()[ordinal() + 1];
    }
}
