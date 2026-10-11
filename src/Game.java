import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** UI-independent, authoritative Ultimate Poker engine. */
public final class Game {
    private Deck deck;
    private final List<Player> players = new ArrayList<>();
    private final List<Card> communityCards = new ArrayList<>();
    private final List<Integer> winnerPlayerIds = new ArrayList<>();
    private GameStage stage = GameStage.WAITING_FOR_PLAYERS;
    private double pot;
    private long roundNumber;
    private List<String> configuredPlayerNames = Collections.emptyList();
    private double configuredStartingMoney;
    private double configuredEntryBet;

    public Game() {
        deck = new Deck();
    }

    /** Compatibility alias. Setup now deals hole cards only, not the board. */
    public void setupRound(List<String> playerNames, double startingMoney, double entryBet) {
        startRound(playerNames, startingMoney, entryBet);
    }

    public void startRound(List<String> playerNames, double startingMoney, double entryBet) {
        requireStage(GameStage.WAITING_FOR_PLAYERS, GameStage.SHOWDOWN);
        validateRoundConfiguration(playerNames, startingMoney, entryBet);
        configuredPlayerNames = Collections.unmodifiableList(new ArrayList<>(playerNames));
        configuredStartingMoney = startingMoney;
        configuredEntryBet = entryBet;
        initializeRound();
    }

    public void startNextRound() {
        requireStage(GameStage.SHOWDOWN);
        initializeRound();
    }

    public void dealFlop() {
        requireStage(GameStage.PREFLOP);
        drawCommunityCards(3);
        stage = GameStage.FLOP_DEALT;
    }

    public void revealFlop() {
        requireStage(GameStage.FLOP_DEALT);
        stage = GameStage.FLOP;
    }

    public void dealTurn() {
        requireStage(GameStage.FLOP);
        drawCommunityCards(1);
        stage = GameStage.TURN_DEALT;
    }

    public void revealTurn() {
        requireStage(GameStage.TURN_DEALT);
        stage = GameStage.TURN;
    }

    public void dealRiver() {
        requireStage(GameStage.TURN);
        drawCommunityCards(1);
        stage = GameStage.RIVER_DEALT;
    }

    /** Ultimate Poker step: expose each player's second card to the table. */
    public void revealPlayerCards() {
        requireStage(GameStage.RIVER_DEALT);
        stage = GameStage.PLAYER_CARDS_REVEALED;
    }

    public void revealRiver() {
        requireStage(GameStage.PLAYER_CARDS_REVEALED);
        stage = GameStage.RIVER;
    }

    public void startShowdown() {
        requireStage(GameStage.RIVER);
        validateCompleteDeal();
        winnerPlayerIds.clear();
        for (Player winner : determineWinners(players, communityCards)) {
            winnerPlayerIds.add(winner.getID());
        }
        stage = GameStage.SHOWDOWN;
    }

    /** Advances exactly one valid state-machine transition. */
    public void advanceGame() {
        switch (stage) {
            case PREFLOP: dealFlop(); break;
            case FLOP_DEALT: revealFlop(); break;
            case FLOP: dealTurn(); break;
            case TURN_DEALT: revealTurn(); break;
            case TURN: dealRiver(); break;
            case RIVER_DEALT: revealPlayerCards(); break;
            case PLAYER_CARDS_REVEALED: revealRiver(); break;
            case RIVER: startShowdown(); break;
            case WAITING_FOR_PLAYERS:
                throw new IllegalStateException("Start a round before advancing the game");
            case SHOWDOWN:
                throw new IllegalStateException("The round is complete; start the next round");
            default:
                throw new IllegalStateException("Unsupported game stage: " + stage);
        }
    }

    public GameStage getStage() { return stage; }
    public GameSnapshot getPublicSnapshot() { return buildSnapshot(null); }

    public GameSnapshot getSnapshotForPlayer(int playerId) {
        findPlayer(playerId);
        return buildSnapshot(playerId);
    }

    private void initializeRound() {
        deck = new Deck();
        deck.createDeck();
        deck.shuffleDeck();
        players.clear();
        communityCards.clear();
        winnerPlayerIds.clear();
        pot = 0;
        int playerId = 1;
        for (String name : configuredPlayerNames) {
            players.add(new Player(name, playerId++, configuredStartingMoney));
        }
        collectEntryBets(configuredEntryBet);
        dealHoleCards();
        stage = GameStage.PREFLOP;
        roundNumber++;
    }

    private void dealHoleCards() {
        ensureDeckHas(players.size() * 2);
        for (int cardIndex = 0; cardIndex < 2; cardIndex++) {
            for (Player player : players) {
                player.receiveCard(deck.drawCard());
            }
        }
    }

    private void drawCommunityCards(int count) {
        ensureDeckHas(count);
        for (int index = 0; index < count; index++) {
            communityCards.add(deck.drawCard());
        }
    }

    private void collectEntryBets(double entryBet) {
        for (Player player : players) {
            player.setBet(entryBet);
            pot += entryBet;
        }
    }

    private GameSnapshot buildSnapshot(Integer privatePlayerId) {
        int publicCardCount = stage.getPublicHoleCardCount();
        int visibleCommunityCount = stage.getVisibleCommunityCardCount();
        List<Card> visibleCommunity = new ArrayList<>(communityCards.subList(
            0, Math.min(visibleCommunityCount, communityCards.size())));
        List<PlayerSnapshot> playerSnapshots = new ArrayList<>();
        for (Player player : players) {
            boolean privateOwner = privatePlayerId != null && player.getID() == privatePlayerId;
            int visibleHoleCount = privateOwner ? player.getHand().size()
                : Math.min(publicCardCount, player.getHand().size());
            List<Card> visibleCards = new ArrayList<>(
                player.getHand().subList(0, visibleHoleCount));
            HandResult finalHand = stage.isShowdown() ? evaluate(player) : null;
            playerSnapshots.add(new PlayerSnapshot(
                player.getID(), player.getName(), player.getMoneyAmount(),
                player.getCurrentBet(), visibleCards,
                player.getHand().size() - visibleHoleCount,
                finalHand, winnerPlayerIds.contains(player.getID())));
        }
        return new GameSnapshot(
            roundNumber, stage, playerSnapshots, visibleCommunity,
            communityCards.size(), pot, deck.cardsLeft(),
            stage.isShowdown() ? winnerPlayerIds : Collections.emptyList());
    }

    private HandResult evaluate(Player player) {
        List<Card> cards = new ArrayList<>(player.getHand());
        cards.addAll(communityCards);
        return new HandEvaluator().evaluate(cards);
    }

    private Player findPlayer(int playerId) {
        for (Player player : players) {
            if (player.getID() == playerId) return player;
        }
        throw new IllegalArgumentException("Unknown player ID: " + playerId);
    }

    private void requireStage(GameStage... allowedStages) {
        for (GameStage allowed : allowedStages) {
            if (stage == allowed) return;
        }
        throw new IllegalStateException("Invalid action while game stage is " + stage);
    }

    private void ensureDeckHas(int requiredCards) {
        if (deck.cardsLeft() < requiredCards) {
            throw new IllegalStateException("The deck does not contain enough cards");
        }
    }

    private void validateCompleteDeal() {
        if (players.size() < 2 || players.size() > 9 || communityCards.size() != 5) {
            throw new IllegalStateException("The round is not ready for showdown");
        }
        Set<Card> dealtCards = new HashSet<>(communityCards);
        if (dealtCards.size() != communityCards.size()) {
            throw new IllegalStateException("The round contains duplicate community cards");
        }
        for (Player player : players) {
            if (player.getHand().size() != 2) {
                throw new IllegalStateException("Every player must have exactly two hole cards");
            }
            for (Card card : player.getHand()) {
                if (!dealtCards.add(card)) {
                    throw new IllegalStateException("The round contains duplicate dealt cards");
                }
            }
        }
    }

    private void validateRoundConfiguration(List<String> names,
                                            double startingMoney,
                                            double entryBet) {
        if (names == null || names.size() < 2 || names.size() > 9) {
            throw new IllegalArgumentException("A round requires between 2 and 9 players");
        }
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Player names must not be blank");
            }
        }
        if (!Double.isFinite(startingMoney) || startingMoney < 0) {
            throw new IllegalArgumentException("Starting money must be finite and non-negative");
        }
        if (!Double.isFinite(entryBet) || entryBet < 0 || entryBet > startingMoney) {
            throw new IllegalArgumentException(
                "Entry bet must be finite, non-negative, and affordable");
        }
    }

    /** Pure helper retained for deterministic evaluator and tie tests. */
    static List<Player> determineWinners(List<Player> players, List<Card> board) {
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException("At least one player is required");
        }
        if (board == null || board.size() != 5) {
            throw new IllegalArgumentException("Exactly five community cards are required");
        }
        HandEvaluator evaluator = new HandEvaluator();
        List<Player> winners = new ArrayList<>();
        HandResult best = null;
        for (Player player : players) {
            if (player == null || player.getHand().size() != 2) {
                throw new IllegalArgumentException("Every player must have two hole cards");
            }
            List<Card> cards = new ArrayList<>(player.getHand());
            cards.addAll(board);
            HandResult result = evaluator.evaluate(cards);
            int comparison = best == null ? 1 : result.compareTo(best);
            if (comparison > 0) {
                winners.clear();
                winners.add(player);
                best = result;
            } else if (comparison == 0) {
                winners.add(player);
            }
        }
        return Collections.unmodifiableList(winners);
    }
}
