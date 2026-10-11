import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Transport-independent registry and command boundary for poker sessions. */
public final class GameSessionService {
    private final ConcurrentMap<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<GameSessionChangeListener> listeners = new CopyOnWriteArrayList<>();

    public String createSession() {
        String sessionId;
        do { sessionId = UUID.randomUUID().toString(); }
        while (sessions.putIfAbsent(sessionId, new GameSession(sessionId)) != null);
        return sessionId;
    }

    public PlayerRegistration joinGame(JoinGameRequest request) {
        if (request == null) throw new InvalidGameCommandException("Join request must not be null");
        PlayerRegistration result = requireSession(request.getSessionId()).join(request.getDisplayName());
        notifyChanged(request.getSessionId());
        return result;
    }

    public GameSnapshot startRound(StartRoundCommand command) {
        if (command == null) throw new InvalidGameCommandException("Start-round command must not be null");
        GameSnapshot result = requireSession(command.getSessionId()).startRound(command.getStartingMoney(), command.getEntryBet());
        notifyChanged(command.getSessionId());
        return result;
    }

    public GameSnapshot advanceGame(AdvanceGameCommand command) {
        if (command == null) throw new InvalidGameCommandException("Advance command must not be null");
        GameSnapshot result = requireSession(command.getSessionId()).advance(command.getExpectedStage());
        notifyChanged(command.getSessionId());
        return result;
    }

    public GameSnapshot startNextRound(StartNextRoundCommand command) {
        if (command == null) throw new InvalidGameCommandException("Next-round command must not be null");
        GameSnapshot result = requireSession(command.getSessionId()).startNextRound();
        notifyChanged(command.getSessionId());
        return result;
    }

    public GameSnapshot getPublicState(String sessionId) { return requireSession(sessionId).publicState(); }

    /** Returns only the requesting player's private view; no target-player parameter exists. */
    public GameSnapshot getPlayerState(PlayerStateRequest request) {
        if (request == null) throw new InvalidGameCommandException("Player-state request must not be null");
        return requireSession(request.getSessionId()).playerState(request.getRequestingPlayerId());
    }

    public List<PlayerRegistration> getPlayers(String sessionId) { return requireSession(sessionId).registrations(); }
    public int getSessionCount() { return sessions.size(); }

    public void addChangeListener(GameSessionChangeListener listener) {
        if (listener == null) throw new IllegalArgumentException("Listener must not be null");
        listeners.addIfAbsent(listener);
    }
    public void removeChangeListener(GameSessionChangeListener listener) { listeners.remove(listener); }

    private void notifyChanged(String sessionId) {
        for (GameSessionChangeListener listener : listeners) {
            try { listener.onSessionChanged(sessionId); }
            catch (RuntimeException exception) { System.err.println("Session listener failed: " + exception.getMessage()); }
        }
    }

    private GameSession requireSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) throw new UnknownGameSessionException(sessionId);
        GameSession session = sessions.get(sessionId);
        if (session == null) throw new UnknownGameSessionException(sessionId);
        return session;
    }
}
