import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, audience-filtered view of the authoritative game state. */
public final class GameSnapshot {
    private final long roundNumber;
    private final GameStage stage;
    private final List<PlayerSnapshot> players;
    private final List<Card> visibleCommunityCards;
    private final int dealtCommunityCardCount;
    private final double pot;
    private final int cardsLeftInDeck;
    private final List<Integer> winnerPlayerIds;

    GameSnapshot(long roundNumber, GameStage stage, List<PlayerSnapshot> players,
                 List<Card> visibleCommunityCards, int dealtCommunityCardCount,
                 double pot, int cardsLeftInDeck, List<Integer> winnerPlayerIds) {
        this.roundNumber = roundNumber;
        this.stage = stage;
        this.players = Collections.unmodifiableList(new ArrayList<>(players));
        this.visibleCommunityCards = Collections.unmodifiableList(new ArrayList<>(visibleCommunityCards));
        this.dealtCommunityCardCount = dealtCommunityCardCount;
        this.pot = pot;
        this.cardsLeftInDeck = cardsLeftInDeck;
        this.winnerPlayerIds = Collections.unmodifiableList(new ArrayList<>(winnerPlayerIds));
    }

    public long getRoundNumber() { return roundNumber; }
    public GameStage getStage() { return stage; }
    public List<PlayerSnapshot> getPlayers() { return players; }
    public List<Card> getVisibleCommunityCards() { return visibleCommunityCards; }
    public int getDealtCommunityCardCount() { return dealtCommunityCardCount; }
    public int getHiddenCommunityCardCount() {
        return dealtCommunityCardCount - visibleCommunityCards.size();
    }
    public double getPot() { return pot; }
    public int getCardsLeftInDeck() { return cardsLeftInDeck; }
    public List<Integer> getWinnerPlayerIds() { return winnerPlayerIds; }

    public PlayerSnapshot getPlayer(int playerId) {
        for (PlayerSnapshot player : players) {
            if (player.getPlayerId() == playerId) {
                return player;
            }
        }
        throw new IllegalArgumentException("Unknown player ID: " + playerId);
    }
}
