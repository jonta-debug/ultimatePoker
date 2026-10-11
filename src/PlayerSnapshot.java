import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable player information filtered for one snapshot audience. */
public final class PlayerSnapshot {
    private final int playerId;
    private final String displayName;
    private final double stack;
    private final double currentBet;
    private final List<Card> visibleCards;
    private final int hiddenCardCount;
    private final HandResult finalHand;
    private final boolean winner;

    PlayerSnapshot(int playerId, String displayName, double stack, double currentBet,
                   List<Card> visibleCards, int hiddenCardCount,
                   HandResult finalHand, boolean winner) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.stack = stack;
        this.currentBet = currentBet;
        this.visibleCards = Collections.unmodifiableList(new ArrayList<>(visibleCards));
        this.hiddenCardCount = hiddenCardCount;
        this.finalHand = finalHand;
        this.winner = winner;
    }

    public int getPlayerId() { return playerId; }
    public String getDisplayName() { return displayName; }
    public double getStack() { return stack; }
    public double getCurrentBet() { return currentBet; }
    public List<Card> getVisibleCards() { return visibleCards; }
    public int getHiddenCardCount() { return hiddenCardCount; }
    public HandResult getFinalHand() { return finalHand; }
    public boolean isWinner() { return winner; }
}
