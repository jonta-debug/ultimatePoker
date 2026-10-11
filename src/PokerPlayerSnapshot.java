import java.util.List;

/** Common immutable player projection used at the transport boundary. */
public interface PokerPlayerSnapshot {
    int getPlayerId();
    String getDisplayName();
    double getStack();
    double getCurrentBet();
    List<Card> getVisibleCards();
    int getHiddenCardCount();
    HandResult getFinalHand();
    boolean isWinner();
}
