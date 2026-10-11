import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Transport-independent multi-mode registry and command boundary. */
public final class GameSessionService {
    private final ConcurrentMap<String,GameSession> sessions=new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<GameSessionChangeListener> listeners=new CopyOnWriteArrayList<>();
    public String createSession(){return createSession(GameMode.ULTIMATE_POKER);}
    public String createSession(GameMode mode){String id;do{id=UUID.randomUUID().toString();}while(sessions.putIfAbsent(id,new GameSession(id,mode))!=null);return id;}
    public GameMode getGameMode(String id){return requireSession(id).getMode();}
    public PlayerRegistration joinGame(JoinGameRequest request){if(request==null)throw new InvalidGameCommandException("Join request must not be null");PlayerRegistration r=requireSession(request.getSessionId()).join(request.getDisplayName());notifyChanged(request.getSessionId());return r;}
    public GameSnapshot startRound(StartRoundCommand command){if(command==null)throw new InvalidGameCommandException("Start-round command must not be null");GameSnapshot s=requireSession(command.getSessionId()).startRound(command.getStartingMoney(),command.getEntryBet());notifyChanged(command.getSessionId());return s;}
    public HoldemSnapshot startHoldemRound(StartHoldemRoundCommand command){if(command==null)throw new InvalidGameCommandException("Hold'em start command must not be null");HoldemSnapshot s=requireSession(command.sessionId()).startHoldem(command.startingStack(),command.smallBlind(),command.bigBlind());notifyChanged(command.sessionId());return s;}
    public GameSnapshot advanceGame(AdvanceGameCommand command){if(command==null)throw new InvalidGameCommandException("Advance command must not be null");GameSnapshot s=requireSession(command.getSessionId()).advance(command.getExpectedStage());notifyChanged(command.getSessionId());return s;}
    public HoldemSnapshot actHoldem(HoldemActionCommand command){if(command==null)throw new InvalidGameCommandException("Hold'em action command must not be null");HoldemSnapshot s=requireSession(command.getSessionId()).holdemAction(command);notifyChanged(command.getSessionId());return s;}
    public GameSnapshot startNextRound(StartNextRoundCommand command){if(command==null)throw new InvalidGameCommandException("Next-round command must not be null");GameSnapshot s=requireSession(command.getSessionId()).startNextRound();notifyChanged(command.getSessionId());return s;}
    public PokerStateSnapshot startNextPokerRound(String sessionId){PokerStateSnapshot s=requireSession(sessionId).startNext();notifyChanged(sessionId);return s;}
    public GameSnapshot getPublicState(String id){return requireSession(id).publicState();}
    public PokerStateSnapshot getPublicPokerState(String id){return requireSession(id).publicPokerState();}
    public GameSnapshot getPlayerState(PlayerStateRequest request){if(request==null)throw new InvalidGameCommandException("Player-state request must not be null");return requireSession(request.getSessionId()).playerState(request.getRequestingPlayerId());}
    public PokerStateSnapshot getPlayerPokerState(PlayerStateRequest request){if(request==null)throw new InvalidGameCommandException("Player-state request must not be null");return requireSession(request.getSessionId()).playerPokerState(request.getRequestingPlayerId());}
    public List<PlayerRegistration> getPlayers(String id){return requireSession(id).registrations();}
    public int getSessionCount(){return sessions.size();}
    public void addChangeListener(GameSessionChangeListener l){if(l==null)throw new IllegalArgumentException("Listener must not be null");listeners.addIfAbsent(l);}
    public void removeChangeListener(GameSessionChangeListener l){listeners.remove(l);}
    private void notifyChanged(String id){for(GameSessionChangeListener l:listeners)try{l.onSessionChanged(id);}catch(RuntimeException e){System.err.println("Session listener failed: "+e.getMessage());}}
    private GameSession requireSession(String id){if(id==null||id.trim().isEmpty())throw new UnknownGameSessionException(id);GameSession s=sessions.get(id);if(s==null)throw new UnknownGameSessionException(id);return s;}
}
