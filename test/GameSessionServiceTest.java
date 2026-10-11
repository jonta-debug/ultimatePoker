import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Transport-independent service, privacy, isolation, and concurrency tests. */
public final class GameSessionServiceTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        testSessionCreationAndJoining();
        testPublicAndPrivateState();
        testInvalidRequestsAndStages();
        testSecondRoundAndAllPlayerCounts();
        testIndependentSessions();
        testConcurrentDuplicateAdvance();
        testConcurrentRoundStart();
        System.out.println("GameSessionServiceTest passed (" + assertions + " assertions)");
    }

    private static void testSessionCreationAndJoining() {
        GameSessionService service = new GameSessionService();
        String firstSession = service.createSession();
        String secondSession = service.createSession();
        check(firstSession != null && !firstSession.isEmpty(), "session ID assigned");
        check(!firstSession.equals(secondSession), "session IDs unique");
        equal(2, service.getSessionCount(), "session registry size");

        PlayerRegistration first = service.joinGame(new JoinGameRequest(firstSession, "Alex"));
        PlayerRegistration second = service.joinGame(new JoinGameRequest(firstSession, "Alex"));
        equal(firstSession, first.getSessionId(), "stable session ID on registration");
        equal(1, first.getPlayerId(), "first stable player ID");
        equal(2, second.getPlayerId(), "second stable player ID");
        equal("Alex", second.getDisplayName(), "display names need not be identities");
        equal(2, service.getPlayers(firstSession).size(), "joined-player list");
        expect(UnsupportedOperationException.class,
            () -> service.getPlayers(firstSession).clear(), "registration list immutable");
    }

    private static void testPublicAndPrivateState() {
        StartedSession started = startedSession(4);
        GameSnapshot publicState = started.service.getPublicState(started.sessionId);
        GameSnapshot playerState = started.service.getPlayerState(
            new PlayerStateRequest(started.sessionId, 2));
        equal(GameStage.PREFLOP, publicState.getStage(), "public stage");
        for (PlayerSnapshot player : publicState.getPlayers()) {
            equal(1, player.getVisibleCards().size(), "public card visibility");
            equal(1, player.getHiddenCardCount(), "public private-card count");
        }
        for (PlayerSnapshot player : playerState.getPlayers()) {
            if (player.getPlayerId() == 2) {
                equal(2, player.getVisibleCards().size(), "requester sees own private card");
                equal(0, player.getHiddenCardCount(), "requester owns both cards");
            } else {
                equal(1, player.getVisibleCards().size(),
                    "requester cannot see another player's private card");
            }
        }
    }

    private static void testInvalidRequestsAndStages() {
        GameSessionService service = new GameSessionService();
        expectCode(GameServiceErrorCode.UNKNOWN_SESSION,
            () -> service.getPublicState("missing"), "unknown session");
        expectCode(GameServiceErrorCode.UNKNOWN_SESSION,
            () -> service.joinGame(new JoinGameRequest(null, "A")), "null session");
        String sessionId = service.createSession();
        service.joinGame(new JoinGameRequest(sessionId, "A"));
        expectCode(GameServiceErrorCode.INVALID_COMMAND,
            () -> service.startRound(new StartRoundCommand(sessionId, 20, 1)),
            "not enough players");
        service.joinGame(new JoinGameRequest(sessionId, "B"));
        service.startRound(new StartRoundCommand(sessionId, 20, 1));
        expectCode(GameServiceErrorCode.UNKNOWN_PLAYER,
            () -> service.getPlayerState(new PlayerStateRequest(sessionId, 99)),
            "invalid player ID");
        expectCode(GameServiceErrorCode.INVALID_GAME_STAGE,
            () -> service.joinGame(new JoinGameRequest(sessionId, "Late")),
            "late join rejected");

        AdvanceGameCommand command = new AdvanceGameCommand(sessionId, GameStage.PREFLOP);
        service.advanceGame(command);
        expectCode(GameServiceErrorCode.INVALID_GAME_STAGE,
            () -> service.advanceGame(command), "duplicate/stale command rejected");
        expectCode(GameServiceErrorCode.INVALID_GAME_STAGE,
            () -> service.startNextRound(new StartNextRoundCommand(sessionId)),
            "early next round rejected");
        expectCode(GameServiceErrorCode.INVALID_COMMAND,
            () -> service.advanceGame(new AdvanceGameCommand(sessionId, null)),
            "missing expected stage rejected");
    }

    private static void testSecondRoundAndAllPlayerCounts() {
        for (int count = 2; count <= 9; count++) {
            StartedSession started = startedSession(count);
            GameSnapshot snapshot = started.service.getPublicState(started.sessionId);
            equal(count, snapshot.getPlayers().size(), "supported player count");
            while (!snapshot.getStage().isShowdown()) {
                snapshot = started.service.advanceGame(
                    new AdvanceGameCommand(started.sessionId, snapshot.getStage()));
            }
            long firstRound = snapshot.getRoundNumber();
            snapshot = started.service.startNextRound(
                new StartNextRoundCommand(started.sessionId));
            equal(GameStage.PREFLOP, snapshot.getStage(), "second round stage");
            equal(firstRound + 1, snapshot.getRoundNumber(), "second round number");
            equal(count, snapshot.getPlayers().size(), "second-round seats retained");
        }
    }

    private static void testIndependentSessions() {
        GameSessionService service = new GameSessionService();
        String first = createStarted(service, List.of("A", "B"));
        String second = createStarted(service, List.of("C", "D", "E"));
        service.advanceGame(new AdvanceGameCommand(first, GameStage.PREFLOP));
        equal(GameStage.FLOP_DEALT, service.getPublicState(first).getStage(),
            "first session advanced");
        equal(GameStage.PREFLOP, service.getPublicState(second).getStage(),
            "second session unaffected");
        equal(2, service.getPublicState(first).getPlayers().size(), "first seats isolated");
        equal(3, service.getPublicState(second).getPlayers().size(), "second seats isolated");
    }

    private static void testConcurrentDuplicateAdvance() throws Exception {
        StartedSession started = startedSession(6);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();
        Runnable action = () -> {
            ready.countDown();
            try {
                fire.await();
                started.service.advanceGame(
                    new AdvanceGameCommand(started.sessionId, GameStage.PREFLOP));
                successes.incrementAndGet();
            } catch (InvalidGameStageException expected) {
                rejected.incrementAndGet();
            } catch (Throwable throwable) {
                unexpected.compareAndSet(null, throwable);
            }
        };
        Thread first = new Thread(action, "advance-1");
        Thread second = new Thread(action, "advance-2");
        first.start(); second.start(); ready.await(); fire.countDown();
        first.join(); second.join();
        equal(null, unexpected.get(), "no unexpected concurrent failure");
        equal(1, successes.get(), "exactly one concurrent command succeeds");
        equal(1, rejected.get(), "stale concurrent command rejected");
        GameSnapshot snapshot = started.service.getPublicState(started.sessionId);
        equal(GameStage.FLOP_DEALT, snapshot.getStage(), "stage advances exactly once");
        equal(3, snapshot.getDealtCommunityCardCount(), "flop dealt exactly once");
    }

    private static void testConcurrentRoundStart() throws Exception {
        GameSessionService service = new GameSessionService();
        String sessionId = service.createSession();
        service.joinGame(new JoinGameRequest(sessionId, "A"));
        service.joinGame(new JoinGameRequest(sessionId, "B"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Runnable action = () -> {
            ready.countDown();
            try {
                fire.await();
                service.startRound(new StartRoundCommand(sessionId, 20, 1));
                successes.incrementAndGet();
            } catch (InvalidGameStageException expected) {
                rejected.incrementAndGet();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };
        Thread first = new Thread(action, "start-1");
        Thread second = new Thread(action, "start-2");
        first.start(); second.start(); ready.await(); fire.countDown();
        first.join(); second.join();
        equal(1, successes.get(), "one concurrent start succeeds");
        equal(1, rejected.get(), "duplicate concurrent start rejected");
        equal(1L, service.getPublicState(sessionId).getRoundNumber(),
            "only one round initialized");
    }

    private static StartedSession startedSession(int playerCount) {
        GameSessionService service = new GameSessionService();
        List<String> names = new ArrayList<>();
        for (int index = 1; index <= playerCount; index++) names.add("P" + index);
        return new StartedSession(service, createStarted(service, names));
    }

    private static String createStarted(GameSessionService service, List<String> names) {
        String sessionId = service.createSession();
        for (String name : names) service.joinGame(new JoinGameRequest(sessionId, name));
        service.startRound(new StartRoundCommand(sessionId, 20, 1));
        return sessionId;
    }

    private static void expectCode(GameServiceErrorCode code, Runnable action, String message) {
        assertions++;
        try { action.run(); }
        catch (GameServiceException exception) {
            if (exception.getErrorCode() == code) return;
            throw new AssertionError(message + ": wrong code " + exception.getErrorCode());
        }
        throw new AssertionError(message + ": expected " + code);
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

    private static final class StartedSession {
        private final GameSessionService service;
        private final String sessionId;
        private StartedSession(GameSessionService service, String sessionId) {
            this.service = service;
            this.sessionId = sessionId;
        }
    }
}
