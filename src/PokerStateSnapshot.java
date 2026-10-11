import java.util.List;

/** Common immutable snapshot surface shared by all poker modes. */
public interface PokerStateSnapshot {
    GameMode getGameMode();
    long getRoundNumber();
    String getPhaseName();
    String getStatusText();
    List<? extends PokerPlayerSnapshot> getPokerPlayers();
    List<Card> getVisibleCommunityCards();
    int getDealtCommunityCardCount();
    int getHiddenCommunityCardCount();
    double getPot();
    int getCardsLeftInDeck();
    List<Integer> getWinnerPlayerIds();
    boolean isHandComplete();
}
