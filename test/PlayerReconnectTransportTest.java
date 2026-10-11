import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Regression coverage for idempotent mobile identity restore and reconnects. */
public final class PlayerReconnectTransportTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static int assertions;

    public static void main(String[] args) throws Exception {
        GameSessionService service = new GameSessionService();
        SessionDirectory directory = new SessionDirectory();
        try (PokerHttpServer server = new PokerHttpServer(
                service, directory).start("127.0.0.1", 0)) {
            String base = "http://127.0.0.1:" + server.getPort();
            JsonNode session = post(base + "/api/sessions",
                "{\"gameMode\":\"TEXAS_HOLDEM\"}", null, 201);
            String code = session.get("joinCode").asText();

            JsonNode firstJoin = post(base + "/api/sessions/" + code + "/join",
                "{\"displayName\":\"iPhone\"}", null, 201);
            String iphoneToken = firstJoin.get("playerToken").asText();
            int iphoneId = firstJoin.get("playerId").asInt();
            equal(1, service.getPlayers(sessionId(directory, code)).size(),
                "first join creates one player");

            JsonNode restored = get(base + "/api/session", iphoneToken, 200);
            equal("iPhone", restored.get("displayName").asText(),
                "page reload restores saved identity");
            equal(1, service.getPlayers(sessionId(directory, code)).size(),
                "page reload does not add a player");

            JsonNode repeatedJoin = post(base + "/api/sessions/" + code + "/join",
                "{\"displayName\":\"Ignored replacement name\"}", iphoneToken, 200);
            equal(iphoneToken, repeatedJoin.get("playerToken").asText(),
                "authenticated join reuses the server-issued token");
            equal(iphoneId, repeatedJoin.get("playerId").asInt(),
                "authenticated join restores the same player ID");
            equal("iPhone", repeatedJoin.get("displayName").asText(),
                "display name cannot replace token identity");
            equal(1, service.getPlayers(sessionId(directory, code)).size(),
                "repeated join does not add a player");

            SocketProbe firstSocket = connect(server, iphoneToken);
            awaitState(firstSocket);
            equal(1, service.getPlayers(sessionId(directory, code)).size(),
                "WebSocket connection does not create a player");
            firstSocket.close();
            SocketProbe reconnectedSocket = connect(server, iphoneToken);
            JsonNode reconnectState = awaitState(reconnectedSocket);
            equal(iphoneId, reconnectState.get("viewerPlayerId").asInt(),
                "WebSocket reconnect keeps the same private viewer");
            equal(1, service.getPlayers(sessionId(directory, code)).size(),
                "WebSocket reconnect does not create a player");

            JsonNode macJoin = post(base + "/api/sessions/" + code + "/join",
                "{\"displayName\":\"Mac\"}", null, 201);
            check(macJoin.get("playerId").asInt() != iphoneId,
                "separate browser receives a separate player ID");
            equal(2, service.getPlayers(sessionId(directory, code)).size(),
                "one iPhone and one Mac produce exactly two players");

            post(base + "/api/sessions/" + code + "/join",
                "{\"displayName\":\"iPhone\"}", "invalid-or-expired-token", 401);
            equal(2, service.getPlayers(sessionId(directory, code)).size(),
                "invalid token is rejected without silently creating a player");

            JsonNode otherSession = post(base + "/api/sessions",
                "{\"gameMode\":\"TEXAS_HOLDEM\"}", null, 201);
            post(base + "/api/sessions/" + otherSession.get("joinCode").asText() + "/join",
                "{\"displayName\":\"iPhone\"}", iphoneToken, 409);
            equal(0, service.getPlayers(sessionId(directory,
                otherSession.get("joinCode").asText())).size(),
                "token from another session cannot create or claim a seat");

            reconnectedSocket.close();
        }
        System.out.println("PlayerReconnectTransportTest passed: "
            + assertions + " assertions");
    }

    private static String sessionId(SessionDirectory directory, String code) {
        return directory.requireSessionId(code);
    }

    private static SocketProbe connect(PokerHttpServer server, String token) {
        LinkedBlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .buildAsync(URI.create("ws://127.0.0.1:" + server.getPort()
                + "/ws/player?token=" + token), new Listener(messages)).join();
        return new SocketProbe(socket, messages);
    }

    private static JsonNode awaitState(SocketProbe probe) throws Exception {
        JsonNode result = probe.messages.poll(5, TimeUnit.SECONDS);
        if (result == null || !"state".equals(result.path("type").asText())) {
            throw new AssertionError("WebSocket did not receive an initial state");
        }
        return result;
    }

    private static JsonNode get(String url, String token, int expected) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer " + token).GET().build();
        return send(request, expected);
    }

    private static JsonNode post(
            String url, String body, String token, int expected) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return send(request.build(), expected);
    }

    private static JsonNode send(HttpRequest request, int expected) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request, HttpResponse.BodyHandlers.ofString());
        equal(expected, response.statusCode(), request.uri() + " status");
        return JSON.readTree(response.body());
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

    private record SocketProbe(WebSocket socket, LinkedBlockingQueue<JsonNode> messages) {
        void close() { socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join(); }
    }

    private static final class Listener implements WebSocket.Listener {
        private final LinkedBlockingQueue<JsonNode> messages;
        private final StringBuilder text = new StringBuilder();

        Listener(LinkedBlockingQueue<JsonNode> messages) { this.messages = messages; }
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override
        public java.util.concurrent.CompletionStage<?> onText(
                WebSocket socket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                try { messages.add(JSON.readTree(text.toString())); }
                catch (Exception exception) { throw new RuntimeException(exception); }
                text.setLength(0);
            }
            socket.request(1);
            return CompletableFuture.completedFuture(null);
        }
    }
}
