import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Dependency-free regression suite. Run with: java -ea PokerLogicTest */
public final class PokerLogicTest {
    private static int assertions;

    private PokerLogicTest() {
    }

    public static void main(String[] args) {
        testDeckIntegrity();
        testEveryHandCategory();
        testTieBreakersAndAceLowStraight();
        testBestFiveOfSeven();
        testSplitWinners();
        testRoundsForEverySupportedPlayerCount();
        testRoundResetAndInvalidConfigurations();
        testRevealStateDoesNotLeakCardsOrWinner();
        testInvalidOperationsFailFast();
        System.out.println("PokerLogicTest passed (" + assertions + " assertions)");
    }

    private static void testDeckIntegrity() {
        Deck deck = new Deck();
        deck.createDeck();
        equal(52, deck.cardsLeft(), "fresh deck size");
        equal(52, new HashSet<>(deck.getCards()).size(), "fresh deck uniqueness");

        Set<Card> drawn = new HashSet<>();
        while (deck.cardsLeft() > 0) {
            check(drawn.add(deck.drawCard()), "drawn card must be unique");
        }
        expectThrows(IllegalStateException.class, deck::drawCard,
            "drawing an empty deck must fail clearly");
    }

    private static void testEveryHandCategory() {
        expectType(HandType.HIGH_CARD, cards("AS", "KD", "9C", "6H", "3S"));
        expectType(HandType.ONE_PAIR, cards("AS", "AD", "9C", "6H", "3S"));
        expectType(HandType.TWO_PAIR, cards("AS", "AD", "9C", "9H", "3S"));
        expectType(HandType.THREE_OF_A_KIND, cards("AS", "AD", "AC", "6H", "3S"));
        expectType(HandType.STRAIGHT, cards("9S", "8D", "7C", "6H", "5S"));
        expectType(HandType.FLUSH, cards("AS", "JS", "9S", "6S", "3S"));
        expectType(HandType.FULL_HOUSE, cards("AS", "AD", "AC", "6H", "6S"));
        expectType(HandType.FOUR_OF_A_KIND, cards("AS", "AD", "AC", "AH", "3S"));
        expectType(HandType.STRAIGHT_FLUSH, cards("9S", "8S", "7S", "6S", "5S"));
    }

    private static void testTieBreakersAndAceLowStraight() {
        HandEvaluator evaluator = new HandEvaluator();
        HandResult wheel = evaluator.evaluate(cards("AS", "2D", "3C", "4H", "5S"));
        HandResult sixHigh = evaluator.evaluate(cards("2S", "3D", "4C", "5H", "6S"));
        equal(List.of(5), wheel.getTiebreaker(), "wheel must be five-high");
        check(sixHigh.compareTo(wheel) > 0, "six-high straight must beat the wheel");

        compareGreater(cards("AS", "AD", "KC", "QH", "JS"),
            cards("AH", "AC", "KD", "QS", "10S"), "pair kicker");
        compareGreater(cards("AS", "KD", "QC", "9H", "8S"),
            cards("AH", "KS", "QD", "9C", "7S"), "high-card fifth kicker");
        compareGreater(cards("KS", "KD", "QC", "QH", "AS"),
            cards("KH", "KC", "JD", "JS", "AS"), "second pair");
        compareGreater(cards("9S", "9D", "9C", "AH", "KS"),
            cards("8S", "8D", "8C", "AH", "KS"), "trip rank");
        compareGreater(cards("AS", "JS", "9S", "6S", "3S"),
            cards("KS", "QS", "9S", "6S", "3S"), "flush high card");
        compareGreater(cards("AS", "AD", "AC", "KH", "KS"),
            cards("QS", "QD", "QC", "AH", "AS"), "full house trip rank");
        compareGreater(cards("AS", "AD", "AC", "AH", "KS"),
            cards("QS", "QD", "QC", "QH", "AS"), "quad rank");
        compareGreater(cards("10S", "9D", "8C", "7H", "6S"),
            cards("9H", "8D", "7C", "6H", "5S"), "straight high card");
        equal(0, new HandEvaluator().evaluate(cards("AS", "KD", "QC", "JH", "10S"))
            .compareTo(new HandEvaluator().evaluate(cards("AH", "KS", "QD", "JC", "10H"))),
            "equivalent hands with different suits tie");
    }

    private static void testBestFiveOfSeven() {
        HandResult result = new HandEvaluator().evaluate(
            cards("AS", "KS", "QS", "JS", "10S", "2D", "2C"));
        equal(HandType.STRAIGHT_FLUSH, result.getType(), "best five of seven");
        equal(List.of(14), result.getTiebreaker(), "royal straight high card");

        expectThrows(IllegalArgumentException.class,
            () -> new HandEvaluator().evaluate(cards("AS", "AS", "QS", "JS", "10S")),
            "duplicate cards must be rejected");
    }

    private static void testSplitWinners() {
        Player first = player("First", 1, "2S", "3D");
        Player second = player("Second", 2, "4S", "5D");
        List<Card> board = cards("AH", "KD", "QC", "JS", "10H");
        List<Player> winners = Game.determineWinners(List.of(first, second), board);
        equal(2, winners.size(), "board straight must split the result");
        check(winners.contains(first) && winners.contains(second),
            "all tied players must be returned");

        Player stronger = player("Stronger", 3, "AS", "AD");
        Player weaker = player("Weaker", 4, "KS", "QD");
        List<Card> pairBoard = cards("2H", "5D", "7C", "9S", "JH");
        equal(List.of(stronger),
            Game.determineWinners(List.of(weaker, stronger), pairBoard),
            "strongest player must win regardless of table order");
    }

    private static void testRoundsForEverySupportedPlayerCount() {
        for (int playerCount = 2; playerCount <= 9; playerCount++) {
            for (int round = 0; round < 3; round++) {
                Game game = new Game();
                game.setupRound(names(playerCount), 20, 1);
                equal(playerCount, game.getPlayers().size(), "player count");
                equal(playerCount, (int) game.getPot(), "entry-bet pot");
                equal(52 - (playerCount * 2) - 5, game.getCardsLeftInDeck(),
                    "remaining deck size");
                equal(5, game.getCommunityCards().size(), "community card count");

                Set<Card> dealt = new HashSet<>(game.getCommunityCards());
                for (Player player : game.getPlayers()) {
                    equal(2, player.getHand().size(), "hole-card count");
                    equal(19.0, player.getMoneyAmount(), "entry bet deducted");
                    dealt.addAll(player.getHand());
                }
                equal((playerCount * 2) + 5, dealt.size(), "all dealt cards unique");
                check(!game.determineWinners().isEmpty(), "complete round has a winner");
            }
        }
    }

    private static void testRoundResetAndInvalidConfigurations() {
        Game game = new Game();
        game.setupRound(names(6), 20, 1);
        List<Card> oldCards = allDealtCards(game);
        game.setupRound(names(3), 50, 2);
        equal(3, game.getPlayers().size(), "new round replaces players");
        equal(6.0, game.getPot(), "new round resets pot");
        equal(41, game.getCardsLeftInDeck(), "new round resets deck");
        equal(11, allDealtCards(game).size(), "new round has correct deal size");
        check(oldCards != allDealtCards(game), "round snapshots are independent");

        expectThrows(IllegalArgumentException.class,
            () -> game.setupRound(names(1), 20, 1), "one-player round rejected");
        expectThrows(IllegalArgumentException.class,
            () -> game.setupRound(names(10), 20, 1), "ten-player round rejected");
        game.setupRound(List.of("A", "A"), 20, 1);
        equal(2, game.getPlayers().size(), "player IDs permit duplicate display names");
        expectThrows(IllegalArgumentException.class,
            () -> game.setupRound(names(2), 1, 2), "unaffordable entry bet rejected");
        equal(2, game.getPlayers().size(), "invalid setup does not destroy active round");
    }

    private static void testRevealStateDoesNotLeakCardsOrWinner() {
        Game game = new Game();
        game.setupRound(names(6), 20, 1);

        for (RevealStage stage : RevealStage.values()) {
            TableViewState state = TableViewState.fromGame(
                game,
                stage.getVisiblePlayerCardCount(),
                stage.getDealtCommunityCardCount(),
                stage.getRevealedCommunityCardCount(),
                stage.shouldShowWinner(),
                stage.shouldShowFinalHandStatus(),
                stage.getStatusText());

            equal(stage.getDealtCommunityCardCount(), state.getCommunityCards().size(),
                "dealt board count for " + stage);
            equal(stage.getRevealedCommunityCardCount(),
                state.getRevealedCommunityCardCount(), "revealed board count for " + stage);
            for (int index = 0; index < state.getCommunityCards().size(); index++) {
                equal(index >= stage.getRevealedCommunityCardCount(),
                    state.isCommunityCardFaceDown(index), "board visibility for " + stage);
            }
            for (PlayerView player : state.getPlayers()) {
                equal(stage.getVisiblePlayerCardCount(), player.getVisibleCards().size(),
                    "visible hole cards for " + stage);
                equal(2 - stage.getVisiblePlayerCardCount(), player.getHiddenCardCount(),
                    "hidden hole cards for " + stage);
                if (!stage.isShowdown()) {
                    check(!player.isWinner(), "winner hidden before showdown");
                    equal("", player.getFinalStatusText(),
                        "final hand hidden before showdown");
                }
            }
            if (!stage.isShowdown()) {
                check(state.getWinnerPlayerIds().isEmpty(),
                    "winner IDs hidden before showdown");
            } else {
                check(!state.getWinnerPlayerIds().isEmpty(),
                    "winner IDs shown at showdown");
            }
        }
    }

    private static void testInvalidOperationsFailFast() {
        Game game = new Game();
        expectThrows(IllegalStateException.class, game::determineWinner,
            "showdown before setup rejected");
        game.setupRound(names(2), 20, 1);
        expectThrows(IllegalStateException.class, game::dealCards,
            "hole cards cannot be dealt twice");
        expectThrows(IllegalStateException.class, game::dealCommunityCards,
            "community cards cannot be dealt twice");
        expectThrows(IllegalStateException.class, () -> game.collectEntryBet(1),
            "entry bets cannot be collected twice");
        expectThrows(UnsupportedOperationException.class,
            () -> game.getCommunityCards().clear(), "community list is read-only");
        expectThrows(UnsupportedOperationException.class,
            () -> game.getPlayers().get(0).getHand().clear(), "hand is read-only");

        Game zeroBetGame = new Game();
        zeroBetGame.setupRound(names(2), 20, 0);
        expectThrows(IllegalStateException.class, () -> zeroBetGame.collectEntryBet(0),
            "zero entry bets still cannot be collected twice");
    }

    private static Player player(String name, int id, String first, String second) {
        Player player = new Player(name, id, 100);
        player.receiveCard(card(first));
        player.receiveCard(card(second));
        return player;
    }

    private static void expectType(HandType expected, List<Card> cards) {
        equal(expected, new HandEvaluator().evaluate(cards).getType(), expected + " detection");
    }

    private static void compareGreater(List<Card> stronger, List<Card> weaker, String message) {
        HandEvaluator evaluator = new HandEvaluator();
        check(evaluator.evaluate(stronger).compareTo(evaluator.evaluate(weaker)) > 0, message);
    }

    private static List<Card> allDealtCards(Game game) {
        List<Card> cards = new ArrayList<>(game.getCommunityCards());
        for (Player player : game.getPlayers()) {
            cards.addAll(player.getHand());
        }
        return cards;
    }

    private static List<String> names(int count) {
        List<String> names = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            names.add("Player " + index);
        }
        return names;
    }

    private static List<Card> cards(String... descriptions) {
        List<Card> cards = new ArrayList<>();
        for (String description : descriptions) {
            cards.add(card(description));
        }
        return cards;
    }

    private static Card card(String description) {
        String rankText = description.substring(0, description.length() - 1);
        char suitText = description.charAt(description.length() - 1);
        Rank rank;
        switch (rankText) {
            case "A": rank = Rank.ACE; break;
            case "K": rank = Rank.KING; break;
            case "Q": rank = Rank.QUEEN; break;
            case "J": rank = Rank.JACK; break;
            case "10": rank = Rank.TEN; break;
            default: rank = Rank.values()[Integer.parseInt(rankText) - 2];
        }
        Suit suit;
        switch (suitText) {
            case 'H': suit = Suit.HEARTS; break;
            case 'D': suit = Suit.DIAMONDS; break;
            case 'C': suit = Suit.CLUBS; break;
            case 'S': suit = Suit.SPADES; break;
            default: throw new IllegalArgumentException("Unknown suit: " + suitText);
        }
        return new Card(suit, rank);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void expectThrows(Class<? extends Throwable> type,
                                     Runnable action, String message) {
        assertions++;
        try {
            action.run();
        } catch (Throwable throwable) {
            if (type.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(message + ": wrong exception " + throwable, throwable);
        }
        throw new AssertionError(message + ": expected " + type.getSimpleName());
    }
}
