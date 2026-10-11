import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable Ultimate Poker player information filtered for one audience. */
public final class PlayerSnapshot implements PokerPlayerSnapshot {
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
        this.playerId=playerId;this.displayName=displayName;this.stack=stack;
        this.currentBet=currentBet;this.visibleCards=Collections.unmodifiableList(new ArrayList<>(visibleCards));
        this.hiddenCardCount=hiddenCardCount;this.finalHand=finalHand;this.winner=winner;
    }
    @Override public int getPlayerId(){return playerId;}
    @Override public String getDisplayName(){return displayName;}
    @Override public double getStack(){return stack;}
    @Override public double getCurrentBet(){return currentBet;}
    @Override public List<Card> getVisibleCards(){return visibleCards;}
    @Override public int getHiddenCardCount(){return hiddenCardCount;}
    @Override public HandResult getFinalHand(){return finalHand;}
    @Override public boolean isWinner(){return winner;}
}
