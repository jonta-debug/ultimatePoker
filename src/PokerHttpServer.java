import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** HTTP/WebSocket adapter around GameSessionService. Contains no poker rules. */
public final class PokerHttpServer implements AutoCloseable {
    private final GameSessionService service;
    private final SessionDirectory directory;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, PlayerConnection> playerConnections = new ConcurrentHashMap<>();
    private final Map<String, Map<String, WsContext>> publicConnections = new ConcurrentHashMap<>();
    private final GameSessionChangeListener changeListener = this::broadcastSession;
    private final Javalin app;

    public PokerHttpServer(GameSessionService service, SessionDirectory directory) {
        if (service == null || directory == null) throw new IllegalArgumentException("Service and directory are required");
        this.service = service; this.directory = directory;
        this.app = Javalin.create(this::configure);
        service.addChangeListener(changeListener);
    }

    private void configure(JavalinConfig config) {
        config.staticFiles.add(files -> { files.hostedPath = "/"; files.directory = "/web"; files.location = Location.CLASSPATH; });
        config.routes.post("/api/sessions", ctx -> {
            HostedSession hosted = createSession();
            respond(ctx, 201, new TransportDtos.SessionDto(hosted.joinCode(), hosted.hostToken()));
        });
        config.routes.post("/api/sessions/{code}/join", ctx -> {
            JsonNode body = readBody(ctx);
            String sessionId = directory.requireSessionId(ctx.pathParam("code"));
            PlayerRegistration registration = service.joinGame(new JoinGameRequest(sessionId, requiredText(body, "displayName")));
            String token = directory.issuePlayerToken(registration);
            respond(ctx, 201, new TransportDtos.JoinDto(token, registration.getPlayerId(), registration.getDisplayName(), directory.getJoinCode(sessionId)));
        });
        config.routes.get("/api/session", ctx -> {
            ClientIdentity identity = requirePlayer(ctx);
            TransportDtos.StateDto state = playerState(identity);
            respond(ctx, 200, new TransportDtos.IdentityDto(identity.displayName(), directory.getJoinCode(identity.sessionId()), state));
        });
        config.routes.get("/api/state/public", ctx -> {
            String sessionId = directory.requireSessionId(ctx.queryParam("code"));
            respond(ctx, 200, publicState(sessionId));
        });
        config.routes.get("/api/state/player", ctx -> respond(ctx, 200, playerState(requirePlayer(ctx))));
        config.routes.post("/api/table/start", ctx -> {
            String sessionId = requireHost(ctx); JsonNode body = readBody(ctx);
            GameSnapshot snapshot = service.startRound(new StartRoundCommand(sessionId, requiredDouble(body, "startingMoney"), requiredDouble(body, "entryBet")));
            respond(ctx, 200, TransportDtos.state(directory.getJoinCode(sessionId), snapshot));
        });
        config.routes.post("/api/table/advance", ctx -> {
            String sessionId = requireHost(ctx); GameStage expected = service.getPublicState(sessionId).getStage();
            GameSnapshot snapshot = service.advanceGame(new AdvanceGameCommand(sessionId, expected));
            respond(ctx, 200, TransportDtos.state(directory.getJoinCode(sessionId), snapshot));
        });
        config.routes.post("/api/table/next-round", ctx -> {
            String sessionId = requireHost(ctx);
            GameSnapshot snapshot = service.startNextRound(new StartNextRoundCommand(sessionId));
            respond(ctx, 200, TransportDtos.state(directory.getJoinCode(sessionId), snapshot));
        });
        config.routes.ws("/ws/player", ws -> {
            ws.onConnect(ctx -> connectPlayer(ctx));
            ws.onClose(ctx -> removePlayerConnection(ctx.sessionId()));
            ws.onError(ctx -> removePlayerConnection(ctx.sessionId()));
        });
        config.routes.ws("/ws/public", ws -> {
            ws.onConnect(ctx -> connectPublic(ctx));
            ws.onClose(ctx -> removePublicConnection(ctx.sessionId()));
            ws.onError(ctx -> removePublicConnection(ctx.sessionId()));
        });
        config.routes.exception(TransportException.class, (error, ctx) -> error(ctx, error.getStatus(), error.getCode(), error.getMessage()));
        config.routes.exception(GameServiceException.class, (error, ctx) -> error(ctx, statusFor(error.getErrorCode()), error.getErrorCode().name(), error.getMessage()));
        config.routes.exception(Exception.class, (error, ctx) -> error(ctx, 400, "MALFORMED_REQUEST", "The request could not be processed"));
    }

    public HostedSession createSession() { return directory.registerSession(service.createSession()); }
    public PokerHttpServer start(String host, int port) { app.start(host, port); return this; }
    public int getPort() { return app.port(); }
    public void stop() { service.removeChangeListener(changeListener); app.stop(); }
    @Override public void close() { stop(); }

    private void connectPlayer(WsContext ctx) {
        try {
            String token = ctx.queryParam("token"); ClientIdentity identity = directory.requirePlayer(token);
            PlayerConnection replacement = new PlayerConnection(token, identity, ctx);
            PlayerConnection old = playerConnections.put(token, replacement);
            if (old != null) safeSend(old.context(), new TransportDtos.ErrorDto("connection", "REPLACED", "A newer connection replaced this one"));
            safeSend(ctx, playerState(identity));
        } catch (RuntimeException error) { safeSend(ctx, websocketError(error)); }
    }

    private void connectPublic(WsContext ctx) {
        try {
            String sessionId = directory.requireSessionId(ctx.queryParam("code"));
            publicConnections.computeIfAbsent(sessionId, key -> new ConcurrentHashMap<>()).put(ctx.sessionId(), ctx);
            safeSend(ctx, publicState(sessionId));
        } catch (RuntimeException error) { safeSend(ctx, websocketError(error)); }
    }

    private void broadcastSession(String sessionId) {
        Map<String, WsContext> publicForSession = publicConnections.get(sessionId);
        if (publicForSession != null) {
            Object state = publicState(sessionId);
            for (WsContext context : publicForSession.values()) safeSend(context, state);
        }
        for (PlayerConnection connection : playerConnections.values()) {
            if (connection.identity().sessionId().equals(sessionId)) safeSend(connection.context(), playerState(connection.identity()));
        }
    }

    private void removePlayerConnection(String websocketId) {
        playerConnections.entrySet().removeIf(entry -> entry.getValue().context().sessionId().equals(websocketId));
    }
    private void removePublicConnection(String websocketId) {
        for (Map<String, WsContext> connections : publicConnections.values()) connections.remove(websocketId);
    }
    private TransportDtos.StateDto publicState(String sessionId) { return TransportDtos.state(directory.getJoinCode(sessionId), service.getPublicState(sessionId)); }
    private TransportDtos.StateDto playerState(ClientIdentity identity) { return TransportDtos.state(directory.getJoinCode(identity.sessionId()), service.getPlayerState(new PlayerStateRequest(identity.sessionId(), identity.playerId()))); }
    private ClientIdentity requirePlayer(Context ctx) { return directory.requirePlayer(bearer(ctx)); }
    private String requireHost(Context ctx) { return directory.requireHostedSession(bearer(ctx)); }
    private String bearer(Context ctx) { String value = ctx.header("Authorization"); return value != null && value.startsWith("Bearer ") ? value.substring(7).trim() : ""; }
    private JsonNode readBody(Context ctx) { try { return json.readTree(ctx.body()); } catch (Exception e) { throw new TransportException(400, "MALFORMED_REQUEST", "Request body must be valid JSON"); } }
    private String requiredText(JsonNode body, String name) { JsonNode node = body == null ? null : body.get(name); if (node == null || !node.isTextual() || node.asText().trim().isEmpty()) throw new TransportException(400, "MALFORMED_REQUEST", name + " is required"); return node.asText().trim(); }
    private double requiredDouble(JsonNode body, String name) { JsonNode node = body == null ? null : body.get(name); if (node == null || !node.isNumber()) throw new TransportException(400, "MALFORMED_REQUEST", name + " must be a number"); return node.asDouble(); }
    private void respond(Context ctx, int status, Object body) throws Exception { ctx.status(status).contentType("application/json").result(json.writeValueAsString(body)); }
    private void error(Context ctx, int status, String code, String message) { try { respond(ctx, status, new TransportDtos.ErrorDto("error", code, message)); } catch (Exception ignored) { ctx.status(status).result("{\"type\":\"error\"}"); } }
    private void safeSend(WsContext ctx, Object body) { try { ctx.send(json.writeValueAsString(body)); } catch (Exception ignored) { removePlayerConnection(ctx.sessionId()); removePublicConnection(ctx.sessionId()); } }
    private Object websocketError(RuntimeException error) { if (error instanceof TransportException transport) return new TransportDtos.ErrorDto("error", transport.getCode(), transport.getMessage()); if (error instanceof GameServiceException serviceError) return new TransportDtos.ErrorDto("error", serviceError.getErrorCode().name(), serviceError.getMessage()); return new TransportDtos.ErrorDto("error", "INVALID_REQUEST", "Connection rejected"); }
    private int statusFor(GameServiceErrorCode code) { return switch (code) { case UNKNOWN_SESSION, UNKNOWN_PLAYER -> 404; case UNAUTHORIZED_PRIVATE_STATE -> 403; case INVALID_GAME_STAGE -> 409; case INVALID_COMMAND -> 400; }; }
    private record PlayerConnection(String token, ClientIdentity identity, WsContext context) { }
}
