import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable render model derived only from an engine snapshot. */
public final class TableViewState {
    private final List<PlayerView> players;
    private final List<Card> communityCards;
    private final int dealtCommunityCardCount;
    private final int revealedCommunityCardCount;
    private final double pot;
    private final int cardsLeftInDeck;
    private final String statusText;
    private final List<Integer> winnerPlayerIds;
    private final boolean showWinner;

    private TableViewState(List<PlayerView> players, List<Card> communityCards,
                           int dealtCommunityCardCount, double pot,
                           int cardsLeftInDeck, String statusText,
                           List<Integer> winnerPlayerIds, boolean showWinner) {
        this.players = Collections.unmodifiableList(new ArrayList<>(players));
        this.communityCards = Collections.unmodifiableList(new ArrayList<>(communityCards));
        this.dealtCommunityCardCount = dealtCommunityCardCount;
        this.revealedCommunityCardCount = communityCards.size();
        this.pot = pot;
        this.cardsLeftInDeck = cardsLeftInDeck;
        this.statusText = statusText;
        this.winnerPlayerIds = Collections.unmodifiableList(new ArrayList<>(winnerPlayerIds));
        this.showWinner = showWinner;
    }

    public static TableViewState fromSnapshot(GameSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Game snapshot must not be null");
        }
        List<PlayerView> views = new ArrayList<>();
        for (PlayerSnapshot player : snapshot.getPlayers()) {
            List<Card> evaluationCards = new ArrayList<>(player.getVisibleCards());
            evaluationCards.addAll(snapshot.getVisibleCommunityCards());
            String visibleStatus = VisibleHandStatusEvaluator.evaluate(evaluationCards);
            String finalStatus = "";
            if (player.getFinalHand() != null) {
                finalStatus = formatHandType(player.getFinalHand());
                if (player.isWinner()) {
                    finalStatus = "WINNER - " + finalStatus;
                }
            }
            views.add(new PlayerView(
                player.getPlayerId(), player.getDisplayName(), player.getStack(),
                player.getCurrentBet(), player.getVisibleCards(),
                player.getHiddenCardCount(), visibleStatus, finalStatus,
                player.isWinner()));
        }

        return new TableViewState(
            views,
            snapshot.getVisibleCommunityCards(),
            snapshot.getDealtCommunityCardCount(),
            snapshot.getPot(),
            snapshot.getCardsLeftInDeck(),
            snapshot.getStage().getStatusText(),
            snapshot.getWinnerPlayerIds(),
            snapshot.getStage().isShowdown());
    }

    private static String formatHandType(HandResult result) {
        return result.getType().name().replace('_', ' ');
    }

    public List<PlayerView> getPlayers() { return players; }
    public List<Card> getCommunityCards() { return communityCards; }
    public int getDealtCommunityCardCount() { return dealtCommunityCardCount; }
    public int getRevealedCommunityCardCount() { return revealedCommunityCardCount; }
    public int getVisiblePlayerCardCount() {
        return players.isEmpty() ? 0 : players.get(0).getVisibleCards().size();
    }
    public boolean isCommunityCardFaceDown(int cardIndex) {
        return cardIndex >= revealedCommunityCardCount
            && cardIndex < dealtCommunityCardCount;
    }
    public double getPot() { return pot; }
    public int getCardsLeftInDeck() { return cardsLeftInDeck; }
    public String getStatusText() { return statusText; }
    public int getWinnerPlayerId() {
        return winnerPlayerIds.isEmpty() ? -1 : winnerPlayerIds.get(0);
    }
    public List<Integer> getWinnerPlayerIds() { return winnerPlayerIds; }
    public boolean isShowWinner() { return showWinner; }
    public boolean isShowFinalHandStatus() { return showWinner; }
}
