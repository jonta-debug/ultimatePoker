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

/** End-to-end HTTP/WebSocket privacy and reconnect checks. */
public final class PokerTransportIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static int assertions;

    public static void main(String[] args) throws Exception {
        GameSessionService service = new GameSessionService();
        try (PokerHttpServer server = new PokerHttpServer(service, new SessionDirectory()).start("127.0.0.1", 0)) {
            String base = "http://127.0.0.1:" + server.getPort();
            JsonNode created = post(base + "/api/sessions", "{}", null, 201);
            String code = created.get("joinCode").asText();
            String hostToken = created.get("hostToken").asText();
            check(code.length() == 6, "session exposes a short code");
            check(!hostToken.isBlank(), "session returns a table token");

            post(base + "/api/sessions/BAD999/join", "{\"displayName\":\"Nope\"}", null, 404);
            JsonNode aliceJoin = post(base + "/api/sessions/" + code + "/join", "{\"displayName\":\"Alice\"}", null, 201);
            JsonNode bobJoin = post(base + "/api/sessions/" + code + "/join", "{\"displayName\":\"Bob\"}", null, 201);
            String aliceToken = aliceJoin.get("playerToken").asText();
            String bobToken = bobJoin.get("playerToken").asText();
            check(!aliceToken.equals(bobToken), "players receive different opaque tokens");

            post(base + "/api/table/start", "{\"startingMoney\":20,\"entryBet\":1}", hostToken, 200);
            JsonNode publicState = get(base + "/api/state/public?code=" + code, null, 200);
            check(publicState.get("viewerPlayerId").isNull(), "public state has no private viewer identity");
            check(publicState.get("players").get(0).get("visibleCards").size() == 1, "public state has only the public first card");
            check(publicState.get("players").get(0).get("hiddenCardCount").asInt() == 1, "public state marks the private second card hidden");

            JsonNode aliceState = get(base + "/api/state/player", aliceToken, 200);
            JsonNode bobState = get(base + "/api/state/player", bobToken, 200);
            check(aliceState.get("players").get(0).get("visibleCards").size() == 2, "Alice receives both Alice cards");
            check(aliceState.get("players").get(1).get("visibleCards").size() == 1, "Alice does not receive Bob's private card");
            check(bobState.get("players").get(1).get("visibleCards").size() == 2, "Bob receives both Bob cards");
            check(bobState.get("players").get(0).get("visibleCards").size() == 1, "Bob does not receive Alice's private card");
            check(!aliceState.equals(bobState), "private snapshots differ by token identity");

            JsonNode tampered = get(base + "/api/state/player?playerId=2", aliceToken, 200);
            check(tampered.equals(aliceState), "a client-supplied player ID cannot change token identity");
            get(base + "/api/state/player", "not-a-real-token", 401);
            JsonNode reconnect = get(base + "/api/session", aliceToken, 200);
            check(reconnect.get("displayName").asText().equals("Alice"), "existing token restores the same player");

            LinkedBlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
            WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://127.0.0.1:" + server.getPort() + "/ws/player?token=" + aliceToken), new JsonListener(messages)).join();
            check(messages.poll(5, TimeUnit.SECONDS) != null, "WebSocket sends an initial private snapshot");
            post(base + "/api/table/advance", "{}", hostToken, 200);
            JsonNode pushed = messages.poll(5, TimeUnit.SECONDS);
            check(pushed != null && pushed.get("stage").asText().equals("FLOP_DEALT"), "WebSocket pushes state after a table command");
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
        System.out.println("PokerTransportIntegrationTest passed: " + assertions + " assertions");
    }

    private static JsonNode get(String url, String token, int expected) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET();
        if (token != null) request.header("Authorization", "Bearer " + token);
        return send(request.build(), expected);
    }
    private static JsonNode post(String url, String body, String token, int expected) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return send(request.build(), expected);
    }
    private static JsonNode send(HttpRequest request, int expected) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == expected, request.uri() + " returned " + response.statusCode() + " expected " + expected);
        return JSON.readTree(response.body());
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }

    private static final class JsonListener implements WebSocket.Listener {
        private final LinkedBlockingQueue<JsonNode> messages; private final StringBuilder text = new StringBuilder();
        JsonListener(LinkedBlockingQueue<JsonNode> messages) { this.messages = messages; }
        @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); }
        @Override public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data); if (last) { try { messages.add(JSON.readTree(text.toString())); } catch (Exception e) { throw new RuntimeException(e); } text.setLength(0); }
            webSocket.request(1); return CompletableFuture.completedFuture(null);
        }
    }
}
