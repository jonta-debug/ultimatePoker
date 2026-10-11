import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shared HTTP/WebSocket adapter for all poker modes. Contains no poker rules. */
public final class PokerHttpServer implements AutoCloseable {
    private final GameSessionService service; private final SessionDirectory directory;
    private final ObjectMapper json=new ObjectMapper();
    private final Map<String,PlayerConnection> playerConnections=new ConcurrentHashMap<>();
    private final Map<String,Map<String,WsContext>> publicConnections=new ConcurrentHashMap<>();
    private final GameSessionChangeListener changeListener=this::broadcastSession;
    private final Javalin app;
    public PokerHttpServer(GameSessionService service,SessionDirectory directory){if(service==null||directory==null)throw new IllegalArgumentException("Service and directory are required");this.service=service;this.directory=directory;app=Javalin.create(this::configure);service.addChangeListener(changeListener);}

    private void configure(JavalinConfig config){
        config.staticFiles.add(f->{f.hostedPath="/";f.directory="/web";f.location=Location.CLASSPATH;});
        config.routes.post("/api/sessions",ctx->{JsonNode body=readOptionalBody(ctx);GameMode mode=optionalMode(body);HostedSession h=createSession(mode);respond(ctx,201,new TransportDtos.SessionDto(h.joinCode(),h.hostToken(),mode.name()));});
        config.routes.post("/api/sessions/{code}/join",ctx->{JsonNode body=readBody(ctx);String sid=directory.requireSessionId(ctx.pathParam("code"));PlayerRegistration r=service.joinGame(new JoinGameRequest(sid,requiredText(body,"displayName")));String token=directory.issuePlayerToken(r);respond(ctx,201,new TransportDtos.JoinDto(token,r.getPlayerId(),r.getDisplayName(),directory.getJoinCode(sid),service.getGameMode(sid).name()));});
        config.routes.get("/api/session",ctx->{ClientIdentity i=requirePlayer(ctx);respond(ctx,200,new TransportDtos.IdentityDto(i.displayName(),directory.getJoinCode(i.sessionId()),playerState(i)));});
        config.routes.get("/api/state/public",ctx->{String sid=directory.requireSessionId(ctx.queryParam("code"));respond(ctx,200,publicState(sid));});
        config.routes.get("/api/state/player",ctx->respond(ctx,200,playerState(requirePlayer(ctx))));
        config.routes.post("/api/table/start",ctx->{String sid=requireHost(ctx);JsonNode body=readBody(ctx);PokerStateSnapshot state;if(service.getGameMode(sid)==GameMode.TEXAS_HOLDEM){state=service.startHoldemRound(new StartHoldemRoundCommand(sid,requiredLong(body,"startingStack"),requiredLong(body,"smallBlind"),requiredLong(body,"bigBlind")));}else{state=service.startRound(new StartRoundCommand(sid,requiredDouble(body,"startingMoney"),requiredDouble(body,"entryBet")));}respond(ctx,200,TransportDtos.publicState(directory.getJoinCode(sid),state));});
        config.routes.post("/api/table/advance",ctx->{String sid=requireHost(ctx);if(service.getGameMode(sid)!=GameMode.ULTIMATE_POKER)throw new InvalidGameCommandException("Hold'em advances through player actions");GameSnapshot current=service.getPublicState(sid);PokerStateSnapshot state=service.advanceGame(new AdvanceGameCommand(sid,current.getStage()));respond(ctx,200,TransportDtos.publicState(directory.getJoinCode(sid),state));});
        config.routes.post("/api/table/next-round",ctx->{String sid=requireHost(ctx);PokerStateSnapshot state=service.startNextPokerRound(sid);respond(ctx,200,TransportDtos.publicState(directory.getJoinCode(sid),state));});
        config.routes.post("/api/holdem/action",ctx->{ClientIdentity i=requirePlayer(ctx);if(service.getGameMode(i.sessionId())!=GameMode.TEXAS_HOLDEM)throw new InvalidGameCommandException("Hold'em action sent to another game mode");JsonNode body=readBody(ctx);HoldemActionType action=parseAction(requiredText(body,"action"));long amount=optionalLong(body,"amount",0);PokerStateSnapshot state=service.actHoldem(new HoldemActionCommand(i.sessionId(),i.playerId(),action,amount));respond(ctx,200,TransportDtos.playerState(directory.getJoinCode(i.sessionId()),i.playerId(),service.getPlayerPokerState(new PlayerStateRequest(i.sessionId(),i.playerId()))));});
        config.routes.ws("/ws/player",ws->{ws.onConnect(this::connectPlayer);ws.onClose(c->removePlayer(c.sessionId()));ws.onError(c->removePlayer(c.sessionId()));});
        config.routes.ws("/ws/public",ws->{ws.onConnect(this::connectPublic);ws.onClose(c->removePublic(c.sessionId()));ws.onError(c->removePublic(c.sessionId()));});
        config.routes.exception(TransportException.class,(e,c)->error(c,e.getStatus(),e.getCode(),e.getMessage()));
        config.routes.exception(GameServiceException.class,(e,c)->error(c,statusFor(e.getErrorCode()),e.getErrorCode().name(),e.getMessage()));
        config.routes.exception(Exception.class,(e,c)->error(c,400,"MALFORMED_REQUEST","The request could not be processed"));
    }
    public HostedSession createSession(){return createSession(GameMode.ULTIMATE_POKER);}
    public HostedSession createSession(GameMode mode){return directory.registerSession(service.createSession(mode));}
    public PokerHttpServer start(String host,int port){app.start(host,port);return this;}
    public int getPort(){return app.port();}
    public void stop(){service.removeChangeListener(changeListener);app.stop();}
    @Override public void close(){stop();}
    private void connectPlayer(WsContext ctx){try{String token=ctx.queryParam("token");ClientIdentity identity=directory.requirePlayer(token);ctx.enableAutomaticPings();PlayerConnection replacement=new PlayerConnection(token,identity,ctx);PlayerConnection old=playerConnections.put(token,replacement);if(old!=null&&!old.context().sessionId().equals(ctx.sessionId())){safeSend(old.context(),new TransportDtos.ErrorDto("connection","REPLACED","A newer connection replaced this one"));old.context().closeSession(4000,"Replaced");}safeSend(ctx,playerState(identity));}catch(RuntimeException e){safeSend(ctx,websocketError(e));}}
    private void connectPublic(WsContext ctx){try{String sid=directory.requireSessionId(ctx.queryParam("code"));ctx.enableAutomaticPings();publicConnections.computeIfAbsent(sid,k->new ConcurrentHashMap<>()).put(ctx.sessionId(),ctx);safeSend(ctx,publicState(sid));}catch(RuntimeException e){safeSend(ctx,websocketError(e));}}
    private void broadcastSession(String sid){Map<String,WsContext> publics=publicConnections.get(sid);if(publics!=null){Object state=publicState(sid);for(WsContext c:publics.values())safeSend(c,state);}for(PlayerConnection c:playerConnections.values())if(c.identity().sessionId().equals(sid))safeSend(c.context(),playerState(c.identity()));}
    private void removePlayer(String id){playerConnections.entrySet().removeIf(e->e.getValue().context().sessionId().equals(id));}
    private void removePublic(String id){for(Map<String,WsContext> m:publicConnections.values())m.remove(id);}
    private TransportDtos.StateDto publicState(String sid){return TransportDtos.publicState(directory.getJoinCode(sid),service.getPublicPokerState(sid));}
    private TransportDtos.StateDto playerState(ClientIdentity i){return TransportDtos.playerState(directory.getJoinCode(i.sessionId()),i.playerId(),service.getPlayerPokerState(new PlayerStateRequest(i.sessionId(),i.playerId())));}
    private ClientIdentity requirePlayer(Context c){return directory.requirePlayer(bearer(c));}
    private String requireHost(Context c){return directory.requireHostedSession(bearer(c));}
    private String bearer(Context c){String v=c.header("Authorization");return v!=null&&v.startsWith("Bearer ")?v.substring(7).trim():"";}
    private JsonNode readBody(Context c){try{return json.readTree(c.body());}catch(Exception e){throw new TransportException(400,"MALFORMED_REQUEST","Request body must be valid JSON");}}
    private JsonNode readOptionalBody(Context c){if(c.body()==null||c.body().isBlank())return json.createObjectNode();return readBody(c);}
    private String requiredText(JsonNode b,String n){JsonNode v=b==null?null:b.get(n);if(v==null||!v.isTextual()||v.asText().trim().isEmpty())throw new TransportException(400,"MALFORMED_REQUEST",n+" is required");return v.asText().trim();}
    private double requiredDouble(JsonNode b,String n){JsonNode v=b==null?null:b.get(n);if(v==null||!v.isNumber())throw new TransportException(400,"MALFORMED_REQUEST",n+" must be a number");return v.asDouble();}
    private long requiredLong(JsonNode b,String n){JsonNode v=b==null?null:b.get(n);if(v==null||!v.canConvertToLong())throw new TransportException(400,"MALFORMED_REQUEST",n+" must be an integer");return v.asLong();}
    private long optionalLong(JsonNode b,String n,long fallback){JsonNode v=b==null?null:b.get(n);if(v==null||v.isNull())return fallback;if(!v.canConvertToLong())throw new TransportException(400,"MALFORMED_REQUEST",n+" must be an integer");return v.asLong();}
    private GameMode optionalMode(JsonNode b){JsonNode v=b==null?null:b.get("gameMode");if(v==null||v.isNull())return GameMode.ULTIMATE_POKER;try{return GameMode.valueOf(v.asText().trim().toUpperCase());}catch(Exception e){throw new TransportException(400,"INVALID_GAME_MODE","Unknown game mode");}}
    private HoldemActionType parseAction(String v){try{return HoldemActionType.valueOf(v.toUpperCase());}catch(Exception e){throw new TransportException(400,"INVALID_ACTION","Unknown Hold'em action");}}
    private void respond(Context c,int status,Object body)throws Exception{c.status(status).contentType("application/json").result(json.writeValueAsString(body));}
    private void error(Context c,int status,String code,String message){try{respond(c,status,new TransportDtos.ErrorDto("error",code,message));}catch(Exception ignored){c.status(status).result("{\"type\":\"error\"}");}}
    private void safeSend(WsContext c,Object body){try{if(!c.session.isOpen()){removePlayer(c.sessionId());removePublic(c.sessionId());return;}c.send(json.writeValueAsString(body));}catch(Exception ignored){removePlayer(c.sessionId());removePublic(c.sessionId());}}
    private Object websocketError(RuntimeException e){if(e instanceof TransportException t)return new TransportDtos.ErrorDto("error",t.getCode(),t.getMessage());if(e instanceof GameServiceException s)return new TransportDtos.ErrorDto("error",s.getErrorCode().name(),s.getMessage());return new TransportDtos.ErrorDto("error","INVALID_REQUEST","Connection rejected");}
    private int statusFor(GameServiceErrorCode c){return switch(c){case UNKNOWN_SESSION,UNKNOWN_PLAYER->404;case UNAUTHORIZED_PRIVATE_STATE->403;case INVALID_GAME_STAGE->409;case INVALID_COMMAND->400;};}
    private record PlayerConnection(String token,ClientIdentity identity,WsContext context){}
}
