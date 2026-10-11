import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Core card, evaluator, winner, and multi-player regression suite. */
public final class PokerLogicTest {
    private static int assertions;

    public static void main(String[] args) {
        testDeckIntegrity();
        testEveryHandCategory();
        testTieBreakersAndAceLowStraight();
        testBestFiveOfSeven();
        testSplitWinners();
        testRoundsForEverySupportedPlayerCount();
        testSecondRound();
        testInvalidConfigurations();
        System.out.println("PokerLogicTest passed (" + assertions + " assertions)");
    }

    private static void testDeckIntegrity() {
        Deck deck = new Deck();
        deck.createDeck();
        equal(52, deck.cardsLeft(), "deck size");
        equal(52, new HashSet<>(deck.getCards()).size(), "deck uniqueness");
        Set<Card> drawn = new HashSet<>();
        while (deck.cardsLeft() > 0) check(drawn.add(deck.drawCard()), "unique draw");
        expect(IllegalStateException.class, deck::drawCard, "empty deck");
    }

    private static void testEveryHandCategory() {
        type(HandType.HIGH_CARD, "AS", "KD", "9C", "6H", "3S");
        type(HandType.ONE_PAIR, "AS", "AD", "9C", "6H", "3S");
        type(HandType.TWO_PAIR, "AS", "AD", "9C", "9H", "3S");
        type(HandType.THREE_OF_A_KIND, "AS", "AD", "AC", "6H", "3S");
        type(HandType.STRAIGHT, "9S", "8D", "7C", "6H", "5S");
        type(HandType.FLUSH, "AS", "JS", "9S", "6S", "3S");
        type(HandType.FULL_HOUSE, "AS", "AD", "AC", "6H", "6S");
        type(HandType.FOUR_OF_A_KIND, "AS", "AD", "AC", "AH", "3S");
        type(HandType.STRAIGHT_FLUSH, "9S", "8S", "7S", "6S", "5S");
    }

    private static void testTieBreakersAndAceLowStraight() {
        HandEvaluator evaluator = new HandEvaluator();
        HandResult wheel = evaluator.evaluate(cards("AS", "2D", "3C", "4H", "5S"));
        HandResult six = evaluator.evaluate(cards("2S", "3D", "4C", "5H", "6S"));
        equal(List.of(5), wheel.getTiebreaker(), "wheel high");
        check(six.compareTo(wheel) > 0, "six-high beats wheel");
        greater(cards("AS", "AD", "KC", "QH", "JS"),
            cards("AH", "AC", "KD", "QS", "10S"), "pair kicker");
        greater(cards("KS", "KD", "QC", "QH", "AS"),
            cards("KH", "KC", "JD", "JS", "AS"), "second pair");
        greater(cards("AS", "JS", "9S", "6S", "3S"),
            cards("KS", "QS", "9S", "6S", "3S"), "flush kicker");
        greater(cards("AS", "AD", "AC", "KH", "KS"),
            cards("QS", "QD", "QC", "AH", "AS"), "full house rank");
    }

    private static void testBestFiveOfSeven() {
        HandResult result = new HandEvaluator().evaluate(
            cards("AS", "KS", "QS", "JS", "10S", "2D", "2C"));
        equal(HandType.STRAIGHT_FLUSH, result.getType(), "best five of seven");
        equal(List.of(14), result.getTiebreaker(), "royal high");
        expect(IllegalArgumentException.class,
            () -> new HandEvaluator().evaluate(cards("AS", "AS", "QS", "JS", "10S")),
            "duplicates rejected");
    }

    private static void testSplitWinners() {
        Player first = player("First", 1, "2S", "3D");
        Player second = player("Second", 2, "4S", "5D");
        List<Player> winners = Game.determineWinners(
            List.of(first, second), cards("AH", "KD", "QC", "JS", "10H"));
        equal(2, winners.size(), "board tie");
        check(winners.contains(first) && winners.contains(second), "all tied winners");
    }

    private static void testRoundsForEverySupportedPlayerCount() {
        for (int count = 2; count <= 9; count++) {
            for (int round = 0; round < 3; round++) {
                Game game = new Game();
                game.startRound(names(count), 20, 1);
                GameSnapshot preflop = game.getPublicSnapshot();
                equal(GameStage.PREFLOP, preflop.getStage(), "preflop stage");
                equal(count, preflop.getPlayers().size(), "player count");
                equal(52 - count * 2, preflop.getCardsLeftInDeck(), "preflop deck");
                equal(0, preflop.getDealtCommunityCardCount(), "no early board cards");
                equal((double) count, preflop.getPot(), "pot");
                while (!game.getStage().isShowdown()) game.advanceGame();
                GameSnapshot showdown = game.getPublicSnapshot();
                equal(5, showdown.getVisibleCommunityCards().size(), "complete board");
                equal(52 - count * 2 - 5, showdown.getCardsLeftInDeck(), "showdown deck");
                check(!showdown.getWinnerPlayerIds().isEmpty(), "winner exists");
                Set<Card> dealt = new HashSet<>(showdown.getVisibleCommunityCards());
                for (PlayerSnapshot player : showdown.getPlayers()) {
                    equal(2, player.getVisibleCards().size(), "showdown hole cards");
                    dealt.addAll(player.getVisibleCards());
                }
                equal(count * 2 + 5, dealt.size(), "all dealt cards unique");
            }
        }
    }

    private static void testSecondRound() {
        Game game = new Game();
        game.startRound(names(4), 50, 2);
        while (!game.getStage().isShowdown()) game.advanceGame();
        GameSnapshot first = game.getPublicSnapshot();
        game.startNextRound();
        GameSnapshot second = game.getPublicSnapshot();
        equal(1L, first.getRoundNumber(), "first round number");
        equal(2L, second.getRoundNumber(), "second round number");
        equal(GameStage.PREFLOP, second.getStage(), "next round preflop");
        equal(8.0, second.getPot(), "pot reset");
        equal(44, second.getCardsLeftInDeck(), "deck reset");
        check(first.getVisibleCommunityCards() != second.getVisibleCommunityCards(),
            "snapshot collections independent");
    }

    private static void testInvalidConfigurations() {
        expect(IllegalArgumentException.class,
            () -> new Game().startRound(names(1), 20, 1), "one player");
        expect(IllegalArgumentException.class,
            () -> new Game().startRound(names(10), 20, 1), "ten players");
        expect(IllegalArgumentException.class,
            () -> new Game().startRound(names(2), 1, 2), "unaffordable ante");
        Game game = new Game();
        game.startRound(List.of("Same", "Same"), 20, 1);
        equal(2, game.getPublicSnapshot().getPlayers().size(), "names are not IDs");
    }

    private static Player player(String name, int id, String first, String second) {
        Player player = new Player(name, id, 100);
        player.receiveCard(card(first)); player.receiveCard(card(second));
        return player;
    }

    private static void type(HandType expected, String... descriptions) {
        equal(expected, new HandEvaluator().evaluate(cards(descriptions)).getType(), "hand type");
    }

    private static void greater(List<Card> stronger, List<Card> weaker, String message) {
        HandEvaluator evaluator = new HandEvaluator();
        check(evaluator.evaluate(stronger).compareTo(evaluator.evaluate(weaker)) > 0, message);
    }

    static List<String> names(int count) {
        List<String> names = new ArrayList<>();
        for (int index = 1; index <= count; index++) names.add("Player " + index);
        return names;
    }

    static List<Card> cards(String... descriptions) {
        List<Card> cards = new ArrayList<>();
        for (String description : descriptions) cards.add(card(description));
        return cards;
    }

    static Card card(String description) {
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
            default: throw new IllegalArgumentException("Unknown suit");
        }
        return new Card(suit, rank);
    }

    static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    static void equal(Object expected, Object actual, String message) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    static void expect(Class<? extends Throwable> type, Runnable action, String message) {
        assertions++;
        try { action.run(); }
        catch (Throwable throwable) {
            if (type.isInstance(throwable)) return;
            throw new AssertionError(message + ": " + throwable, throwable);
        }
        throw new AssertionError(message + ": expected " + type.getSimpleName());
    }
}
