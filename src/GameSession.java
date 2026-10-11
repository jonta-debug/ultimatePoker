import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Locked owner of exactly one mode-specific authoritative engine. */
final class GameSession {
    private final String sessionId;
    private final GameMode mode;
    private final PokerGameEngine engine;
    private final List<PlayerRegistration> registrations = new ArrayList<>();

    GameSession(String sessionId) { this(sessionId, GameMode.ULTIMATE_POKER); }
    GameSession(String sessionId, GameMode mode) {
        this.sessionId=sessionId;
        this.mode=mode==null?GameMode.ULTIMATE_POKER:mode;
        engine=this.mode==GameMode.TEXAS_HOLDEM?new TexasHoldemGame():new UltimatePokerGameEngine();
    }

    synchronized PlayerRegistration join(String name) {
        if (!isWaiting()) throw new InvalidGameStageException("Players may join only before the first round starts");
        if(name==null||name.trim().isEmpty())throw new InvalidGameCommandException("Display name must not be blank");
        if(registrations.size()>=9)throw new InvalidGameCommandException("A session supports at most 9 players");
        PlayerRegistration result=new PlayerRegistration(sessionId,registrations.size()+1,name.trim());registrations.add(result);return result;
    }
    synchronized GameSnapshot startRound(double money,double entryBet){requireMode(GameMode.ULTIMATE_POKER);ensurePlayers();try{engine.start(registrations,PokerGameConfig.ultimate(money,entryBet));return(GameSnapshot)engine.publicState();}catch(IllegalStateException e){throw new InvalidGameStageException(e.getMessage(),e);}catch(IllegalArgumentException e){throw new InvalidGameCommandException(e.getMessage(),e);}}
    synchronized HoldemSnapshot startHoldem(long stack,long small,long big){requireMode(GameMode.TEXAS_HOLDEM);ensurePlayers();try{engine.start(registrations,PokerGameConfig.holdem(stack,small,big));return(HoldemSnapshot)engine.publicState();}catch(GameServiceException e){throw e;}catch(IllegalStateException e){throw new InvalidGameStageException(e.getMessage(),e);}catch(IllegalArgumentException e){throw new InvalidGameCommandException(e.getMessage(),e);}}
    synchronized GameSnapshot advance(GameStage expected){requireMode(GameMode.ULTIMATE_POKER);UltimatePokerGameEngine game=(UltimatePokerGameEngine)engine;if(expected==null)throw new InvalidGameCommandException("Expected game stage is required");if(game.getStage()!=expected)throw new InvalidGameStageException("Stale command: expected "+expected+" but session is at "+game.getStage());try{game.advance();return game.publicState();}catch(IllegalStateException e){throw new InvalidGameStageException(e.getMessage(),e);}}
    synchronized HoldemSnapshot holdemAction(HoldemActionCommand command){requireMode(GameMode.TEXAS_HOLDEM);try{((TexasHoldemGame)engine).act(command.getPlayerId(),command.getAction(),command.getAmount());return(HoldemSnapshot)engine.publicState();}catch(GameServiceException e){throw e;}catch(IllegalStateException|IllegalArgumentException e){throw new InvalidGameCommandException(e.getMessage(),e);}}
    synchronized PokerStateSnapshot startNext(){try{engine.startNextRound();return engine.publicState();}catch(IllegalStateException e){throw new InvalidGameStageException("A next round can start only after showdown",e);}}
    synchronized GameSnapshot startNextRound(){requireMode(GameMode.ULTIMATE_POKER);return(GameSnapshot)startNext();}
    synchronized PokerStateSnapshot publicPokerState(){return engine.publicState();}
    synchronized GameSnapshot publicState(){requireMode(GameMode.ULTIMATE_POKER);return(GameSnapshot)engine.publicState();}
    synchronized PokerStateSnapshot playerPokerState(int id){requireRegisteredPlayer(id);if(isWaiting())return engine.publicState();try{return engine.playerState(id);}catch(IllegalArgumentException e){throw new UnknownSessionPlayerException(sessionId,id);}}
    synchronized GameSnapshot playerState(int id){requireMode(GameMode.ULTIMATE_POKER);return(GameSnapshot)playerPokerState(id);}
    synchronized List<PlayerRegistration> registrations(){return Collections.unmodifiableList(new ArrayList<>(registrations));}
    GameMode getMode(){return mode;}
    private boolean isWaiting(){return engine.publicState().getPhaseName().equals("WAITING_FOR_PLAYERS");}
    private void ensurePlayers(){if(registrations.size()<2)throw new InvalidGameCommandException("At least two joined players are required to start a round");}
    private void requireMode(GameMode expected){if(mode!=expected)throw new InvalidGameCommandException("Command is not valid for "+mode);}
    private void requireRegisteredPlayer(int id){for(PlayerRegistration r:registrations)if(r.getPlayerId()==id)return;throw new UnknownSessionPlayerException(sessionId,id);}
}
