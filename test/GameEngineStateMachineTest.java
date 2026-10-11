import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** State-transition, snapshot privacy, and immutability regression suite. */
public final class GameEngineStateMachineTest {
    private static int assertions;

    public static void main(String[] args) {
        testValidTransitionsAndDealTiming();
        testInvalidTransitions();
        testAudiencePrivacy();
        testSnapshotImmutabilityAndIsolation();
        testAllPlayerCountsAndSecondRound();
        System.out.println("GameEngineStateMachineTest passed (" + assertions + " assertions)");
    }

    private static void testValidTransitionsAndDealTiming() {
        Game game = started(4);
        state(game, GameStage.PREFLOP, 0, 0, 44);
        game.dealFlop(); state(game, GameStage.FLOP_DEALT, 3, 0, 41);
        game.revealFlop(); state(game, GameStage.FLOP, 3, 3, 41);
        game.dealTurn(); state(game, GameStage.TURN_DEALT, 4, 3, 40);
        game.revealTurn(); state(game, GameStage.TURN, 4, 4, 40);
        game.dealRiver(); state(game, GameStage.RIVER_DEALT, 5, 4, 39);
        game.revealPlayerCards();
        state(game, GameStage.PLAYER_CARDS_REVEALED, 5, 4, 39);
        game.revealRiver(); state(game, GameStage.RIVER, 5, 5, 39);
        check(game.getPublicSnapshot().getWinnerPlayerIds().isEmpty(),
            "winner hidden before showdown");
        game.startShowdown(); state(game, GameStage.SHOWDOWN, 5, 5, 39);
        check(!game.getPublicSnapshot().getWinnerPlayerIds().isEmpty(),
            "winner available at showdown");
    }

    private static void testInvalidTransitions() {
        Game waiting = new Game();
        expect(IllegalStateException.class, waiting::advanceGame, "advance before round");
        Game game = started(3);
        expect(IllegalStateException.class, game::dealTurn, "turn before flop");
        expect(IllegalStateException.class, game::dealRiver, "river before turn");
        expect(IllegalStateException.class, game::startShowdown, "early showdown");
        game.dealFlop();
        expect(IllegalStateException.class, game::dealFlop, "duplicate flop");
        expect(IllegalStateException.class, game::startShowdown, "incomplete showdown");
        game.revealFlop(); game.dealTurn(); game.revealTurn(); game.dealRiver();
        game.revealPlayerCards(); game.revealRiver(); game.startShowdown();
        expect(IllegalStateException.class, game::advanceGame, "advance completed round");
        expect(IllegalStateException.class, game::startShowdown, "duplicate showdown");
    }

    private static void testAudiencePrivacy() {
        Game game = started(4);
        GameSnapshot publicState = game.getPublicSnapshot();
        GameSnapshot playerTwoState = game.getSnapshotForPlayer(2);
        for (PlayerSnapshot player : publicState.getPlayers()) {
            equal(1, player.getVisibleCards().size(), "public sees one Ultimate Poker card");
            equal(1, player.getHiddenCardCount(), "public second card hidden");
        }
        for (PlayerSnapshot player : playerTwoState.getPlayers()) {
            if (player.getPlayerId() == 2) {
                equal(2, player.getVisibleCards().size(), "owner sees both private cards");
                equal(0, player.getHiddenCardCount(), "owner has no hidden own cards");
            } else {
                equal(1, player.getVisibleCards().size(), "other private cards remain hidden");
            }
        }
        expect(IllegalArgumentException.class, () -> game.getSnapshotForPlayer(99),
            "unknown audience rejected");
    }

    private static void testSnapshotImmutabilityAndIsolation() {
        Game game = started(2);
        GameSnapshot before = game.getPublicSnapshot();
        expect(UnsupportedOperationException.class, () -> before.getPlayers().clear(),
            "player snapshot list immutable");
        expect(UnsupportedOperationException.class,
            () -> before.getPlayer(1).getVisibleCards().clear(), "hole cards immutable");
        expect(UnsupportedOperationException.class,
            () -> before.getVisibleCommunityCards().clear(), "board immutable");
        expect(UnsupportedOperationException.class,
            () -> before.getWinnerPlayerIds().clear(), "winner IDs immutable");
        game.dealFlop();
        equal(GameStage.PREFLOP, before.getStage(), "old snapshot does not change");
        equal(0, before.getDealtCommunityCardCount(), "old board remains unchanged");
    }

    private static void testAllPlayerCountsAndSecondRound() {
        for (int count = 2; count <= 9; count++) {
            Game game = started(count);
            while (!game.getStage().isShowdown()) game.advanceGame();
            GameSnapshot result = game.getPublicSnapshot();
            Set<Card> cards = new HashSet<>(result.getVisibleCommunityCards());
            for (PlayerSnapshot player : result.getPlayers()) cards.addAll(player.getVisibleCards());
            equal(count * 2 + 5, cards.size(), "no duplicate dealt cards");
            game.startNextRound();
            equal(GameStage.PREFLOP, game.getStage(), "second round starts at preflop");
            equal(2L, game.getPublicSnapshot().getRoundNumber(), "round incremented");
        }
    }

    private static Game started(int count) {
        Game game = new Game();
        game.startRound(PokerLogicTest.names(count), 20, 1);
        return game;
    }

    private static void state(Game game, GameStage stage, int dealt, int visible, int deck) {
        GameSnapshot snapshot = game.getPublicSnapshot();
        equal(stage, snapshot.getStage(), "stage");
        equal(dealt, snapshot.getDealtCommunityCardCount(), "dealt board cards");
        equal(visible, snapshot.getVisibleCommunityCards().size(), "visible board cards");
        equal(deck, snapshot.getCardsLeftInDeck(), "deck size");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual, String message) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void expect(Class<? extends Throwable> type, Runnable action, String message) {
        assertions++;
        try { action.run(); }
        catch (Throwable throwable) {
            if (type.isInstance(throwable)) return;
            throw new AssertionError(message + ": " + throwable, throwable);
        }
        throw new AssertionError(message + ": expected " + type.getSimpleName());
    }
}
