import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable snapshot of the game data needed by PokerTablePanel. */
public final class TableViewState {
    private final List<PlayerView> players;
    private final List<Card> communityCards;
    private final int dealtCommunityCardCount;
    private final int revealedCommunityCardCount;
    private final int visiblePlayerCardCount;
    private final double pot;
    private final int cardsLeftInDeck;
    private final String statusText;
    private final int winnerPlayerId;
    private final List<Integer> winnerPlayerIds;
    private final boolean showWinner;
    private final boolean showFinalHandStatus;

    public TableViewState(List<PlayerView> players, List<Card> communityCards, double pot,
                          int cardsLeftInDeck, String statusText, int winnerPlayerId) {
        this(players, communityCards, communityCards.size(), communityCards.size(), 2,
            pot, cardsLeftInDeck, statusText, winnerPlayerId,
            winnerPlayerId >= 0, winnerPlayerId >= 0);
    }

    public TableViewState(List<PlayerView> players, List<Card> communityCards,
                          int dealtCommunityCardCount, int revealedCommunityCardCount,
                          int visiblePlayerCardCount, double pot, int cardsLeftInDeck,
                          String statusText, int winnerPlayerId,
                          boolean showWinner, boolean showFinalHandStatus) {
        this(players, communityCards, dealtCommunityCardCount, revealedCommunityCardCount,
            visiblePlayerCardCount, pot, cardsLeftInDeck, statusText,
            winnerPlayerId < 0 ? Collections.emptyList() : List.of(winnerPlayerId),
            showWinner, showFinalHandStatus);
    }

    private TableViewState(List<PlayerView> players, List<Card> communityCards,
                           int dealtCommunityCardCount, int revealedCommunityCardCount,
                           int visiblePlayerCardCount, double pot, int cardsLeftInDeck,
                           String statusText, List<Integer> winnerPlayerIds,
                           boolean showWinner, boolean showFinalHandStatus) {
        this.players = Collections.unmodifiableList(new ArrayList<>(players));
        this.communityCards = Collections.unmodifiableList(new ArrayList<>(communityCards));
        this.dealtCommunityCardCount = dealtCommunityCardCount;
        this.revealedCommunityCardCount = revealedCommunityCardCount;
        this.visiblePlayerCardCount = visiblePlayerCardCount;
        this.pot = pot;
        this.cardsLeftInDeck = cardsLeftInDeck;
        this.statusText = statusText;
        this.winnerPlayerIds = Collections.unmodifiableList(new ArrayList<>(winnerPlayerIds));
        this.winnerPlayerId = this.winnerPlayerIds.isEmpty() ? -1 : this.winnerPlayerIds.get(0);
        this.showWinner = showWinner;
        this.showFinalHandStatus = showFinalHandStatus;
    }

    public static TableViewState fromGame(Game game, boolean showAllCards, boolean showWinner) {
        return fromGame(game, showAllCards ? 2 : 0, 5, 5,
            showWinner, showWinner, null);
    }

    public static TableViewState fromGame(Game game, boolean showAllPlayerCards,
                                          int visibleCommunityCardCount,
                                          boolean showWinner, String statusText) {
        return fromGame(game, showAllPlayerCards ? 2 : 0,
            visibleCommunityCardCount, visibleCommunityCardCount,
            showWinner, showWinner, statusText);
    }

    public static TableViewState fromGame(Game game, int visiblePlayerCardCount,
                                          int dealtCommunityCardCount,
                                          int revealedCommunityCardCount,
                                          boolean showWinner,
                                          boolean showFinalHandStatus,
                                          String statusText) {
        validate(game, visiblePlayerCardCount, dealtCommunityCardCount,
            revealedCommunityCardCount);
        if (showWinner && revealedCommunityCardCount != 5) {
            throw new IllegalArgumentException(
                "Winner information is only available after all community cards are revealed");
        }
        if (showFinalHandStatus && !showWinner) {
            throw new IllegalArgumentException(
                "Final hand status requires showdown winner information");
        }
        if (showWinner && visiblePlayerCardCount != 2) {
            throw new IllegalArgumentException(
                "Showdown requires both player cards to be visible");
        }

        List<Player> winners = showWinner
            ? game.determineWinners()
            : Collections.emptyList();
        List<Integer> winnerIds = new ArrayList<>();
        for (Player winner : winners) {
            winnerIds.add(winner.getID());
        }
        List<Card> revealedCommunityCards = new ArrayList<>(game.getCommunityCards()
            .subList(0, Math.min(revealedCommunityCardCount, game.getCommunityCards().size())));
        List<PlayerView> playerViews = new ArrayList<>();
        String winnerHand = "";

        for (Player player : game.getPlayers()) {
            boolean isWinner = showWinner && winnerIds.contains(player.getID());
            int visibleCardCount = Math.min(visiblePlayerCardCount, player.getHand().size());
            List<Card> visiblePlayerCards = new ArrayList<>(
                player.getHand().subList(0, visibleCardCount));
            int hiddenCardCount = player.getHand().size() - visibleCardCount;

            List<Card> visibleEvaluationCards = new ArrayList<>(visiblePlayerCards);
            visibleEvaluationCards.addAll(revealedCommunityCards);
            String visibleStatus = VisibleHandStatusEvaluator.evaluate(visibleEvaluationCards);

            String finalStatus = "";
            if (showFinalHandStatus) {
                HandResult result = game.evaluatePlayer(player);
                finalStatus = formatHandType(result);
                if (isWinner) {
                    winnerHand = finalStatus;
                    finalStatus = "WINNER - " + finalStatus;
                }
            }

            playerViews.add(new PlayerView(
                player.getID(),
                player.getName(),
                player.getMoneyAmount(),
                player.getCurrentBet(),
                visiblePlayerCards,
                hiddenCardCount,
                visibleStatus,
                finalStatus,
                isWinner));
        }

        String resolvedStatusText = statusText;
        if (resolvedStatusText == null) {
            if (!winners.isEmpty()) {
                StringBuilder names = new StringBuilder();
                for (Player winner : winners) {
                    if (names.length() > 0) {
                        names.append(", ");
                    }
                    names.append(winner.getName());
                }
                resolvedStatusText = (winners.size() == 1 ? "Winner: " : "Tie: ")
                    + names + " - " + winnerHand;
            } else {
                resolvedStatusText = "Round ready";
            }
        }

        int dealtCount = Math.min(dealtCommunityCardCount, game.getCommunityCards().size());
        List<Card> dealtCommunityCards = game.getCommunityCards().subList(0, dealtCount);
        return new TableViewState(
            playerViews,
            dealtCommunityCards,
            dealtCount,
            Math.min(revealedCommunityCardCount, dealtCount),
            visiblePlayerCardCount,
            game.getPot(),
            game.getCardsLeftInDeck(),
            resolvedStatusText,
            winnerIds,
            showWinner,
            showFinalHandStatus);
    }

    private static void validate(Game game, int visiblePlayerCardCount,
                                 int dealtCommunityCardCount,
                                 int revealedCommunityCardCount) {
        if (game == null) {
            throw new IllegalArgumentException("Game must not be null");
        }
        if (visiblePlayerCardCount < 0 || visiblePlayerCardCount > 2) {
            throw new IllegalArgumentException(
                "Visible player card count must be between 0 and 2");
        }
        if (dealtCommunityCardCount < 0 || dealtCommunityCardCount > 5) {
            throw new IllegalArgumentException(
                "Dealt community card count must be between 0 and 5");
        }
        if (revealedCommunityCardCount < 0
                || revealedCommunityCardCount > dealtCommunityCardCount) {
            throw new IllegalArgumentException(
                "Revealed community cards must be between 0 and the dealt count");
        }
        if (dealtCommunityCardCount > game.getCommunityCards().size()) {
            throw new IllegalArgumentException(
                "Requested community cards have not been dealt by the game");
        }
        for (Player player : game.getPlayers()) {
            if (visiblePlayerCardCount > player.getHand().size()) {
                throw new IllegalArgumentException(
                    "Requested player cards have not been dealt by the game");
            }
        }
    }

    private static String formatHandType(HandResult result) {
        return result.getType().name().replace('_', ' ');
    }

    public List<PlayerView> getPlayers() {
        return players;
    }

    public List<Card> getCommunityCards() {
        return communityCards;
    }

    public int getDealtCommunityCardCount() {
        return dealtCommunityCardCount;
    }

    public int getRevealedCommunityCardCount() {
        return revealedCommunityCardCount;
    }

    public int getVisiblePlayerCardCount() {
        return visiblePlayerCardCount;
    }

    public boolean isCommunityCardFaceDown(int cardIndex) {
        return cardIndex >= revealedCommunityCardCount
            && cardIndex < dealtCommunityCardCount;
    }

    public double getPot() {
        return pot;
    }

    public int getCardsLeftInDeck() {
        return cardsLeftInDeck;
    }

    public String getStatusText() {
        return statusText;
    }

    public int getWinnerPlayerId() {
        return winnerPlayerId;
    }

    public List<Integer> getWinnerPlayerIds() {
        return winnerPlayerIds;
    }

    public boolean isShowWinner() {
        return showWinner;
    }

    public boolean isShowFinalHandStatus() {
        return showFinalHandStatus;
    }
}
