import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Locked owner of exactly one authoritative Game instance. */
final class GameSession {
    private final String sessionId;
    private final Game game;
    private final List<PlayerRegistration> registrations;

    GameSession(String sessionId) { this.sessionId = sessionId; this.game = new Game(); this.registrations = new ArrayList<>(); }

    synchronized PlayerRegistration join(String displayName) {
        if (game.getStage() != GameStage.WAITING_FOR_PLAYERS) throw new InvalidGameStageException("Players may join only before the first round starts");
        if (displayName == null || displayName.trim().isEmpty()) throw new InvalidGameCommandException("Display name must not be blank");
        if (registrations.size() >= 9) throw new InvalidGameCommandException("A session supports at most 9 players");
        PlayerRegistration registration = new PlayerRegistration(sessionId, registrations.size() + 1, displayName.trim());
        registrations.add(registration); return registration;
    }

    synchronized GameSnapshot startRound(double startingMoney, double entryBet) {
        if (registrations.size() < 2) throw new InvalidGameCommandException("At least two joined players are required to start a round");
        List<String> names = new ArrayList<>(); for (PlayerRegistration registration : registrations) names.add(registration.getDisplayName());
        try { game.startRound(names, startingMoney, entryBet); return game.getPublicSnapshot(); }
        catch (IllegalStateException exception) { throw new InvalidGameStageException("Cannot start a round while stage is " + game.getStage(), exception); }
        catch (IllegalArgumentException exception) { throw new InvalidGameCommandException(exception.getMessage(), exception); }
    }

    synchronized GameSnapshot advance(GameStage expectedStage) {
        if (expectedStage == null) throw new InvalidGameCommandException("Expected game stage is required");
        if (game.getStage() != expectedStage) throw new InvalidGameStageException("Stale command: expected " + expectedStage + " but session is at " + game.getStage());
        try { game.advanceGame(); return game.getPublicSnapshot(); }
        catch (IllegalStateException exception) { throw new InvalidGameStageException(exception.getMessage(), exception); }
    }

    synchronized GameSnapshot startNextRound() {
        try { game.startNextRound(); return game.getPublicSnapshot(); }
        catch (IllegalStateException exception) { throw new InvalidGameStageException("A next round can start only after showdown", exception); }
    }

    synchronized GameSnapshot publicState() { return game.getPublicSnapshot(); }

    synchronized GameSnapshot playerState(int requestingPlayerId) {
        requireRegisteredPlayer(requestingPlayerId);
        // A token may reconnect while the lobby is still waiting. No cards exist yet,
        // so returning the empty public snapshot is safe and keeps the connection alive.
        if (game.getStage() == GameStage.WAITING_FOR_PLAYERS) return game.getPublicSnapshot();
        try { return game.getSnapshotForPlayer(requestingPlayerId); }
        catch (IllegalArgumentException exception) { throw new UnknownSessionPlayerException(sessionId, requestingPlayerId); }
    }

    synchronized List<PlayerRegistration> registrations() { return Collections.unmodifiableList(new ArrayList<>(registrations)); }
    private void requireRegisteredPlayer(int playerId) {
        for (PlayerRegistration registration : registrations) if (registration.getPlayerId() == playerId) return;
        throw new UnknownSessionPlayerException(sessionId, playerId);
    }
}
