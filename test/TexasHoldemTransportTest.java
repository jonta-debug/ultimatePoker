import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Hold'em mode selection, token-bound actions, privacy, and live update tests. */
public final class TexasHoldemTransportTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static int assertions;

    public static void main(String[] args) throws Exception {
        GameSessionService service = new GameSessionService();
        try (PokerHttpServer server = new PokerHttpServer(service, new SessionDirectory())
                .start("127.0.0.1", 0)) {
            String base = "http://127.0.0.1:" + server.getPort();
            JsonNode created = post(base + "/api/sessions",
                "{\"gameMode\":\"TEXAS_HOLDEM\"}", null, 201);
            equal("TEXAS_HOLDEM", created.get("gameMode").asText(), "mode creation");
            String code = created.get("joinCode").asText();
            String host = created.get("hostToken").asText();

            JsonNode alice = post(base + "/api/sessions/" + code + "/join",
                "{\"displayName\":\"Alice\"}", null, 201);
            JsonNode bob = post(base + "/api/sessions/" + code + "/join",
                "{\"displayName\":\"Bob\"}", null, 201);
            int aliceId = alice.get("playerId").asInt();
            int bobId = bob.get("playerId").asInt();
            String aliceToken = alice.get("playerToken").asText();
            String bobToken = bob.get("playerToken").asText();

            Probe aliceProbe = connect(server, aliceToken);
            Probe bobProbe = connect(server, bobToken);
            await(aliceProbe, "WAITING_FOR_PLAYERS");
            await(bobProbe, "WAITING_FOR_PLAYERS");

            JsonNode started = post(base + "/api/table/start",
                "{\"startingStack\":100,\"smallBlind\":1,\"bigBlind\":2}", host, 200);
            equal("TEXAS_HOLDEM", started.get("gameMode").asText(), "started mode");
            JsonNode aliceState = await(aliceProbe, "PREFLOP");
            JsonNode bobState = await(bobProbe, "PREFLOP");
            equal(2, aliceState.get("players").get(0).get("visibleCards").size(),
                "Alice private cards");
            equal(0, aliceState.get("players").get(1).get("visibleCards").size(),
                "Alice cannot see Bob");
            equal(2, bobState.get("players").get(1).get("visibleCards").size(),
                "Bob private cards");
            equal(0, started.get("players").get(0).get("visibleCards").size(),
                "public hides holes");
            check(contains(aliceState.get("players").get(0).get("legalActions"), "CALL"),
                "actor legal actions exposed");

            post(base + "/api/holdem/action", "{\"action\":\"CALL\"}", aliceToken, 200);
            await(aliceProbe, "PREFLOP");
            await(bobProbe, "PREFLOP");
            post(base + "/api/holdem/action", "{\"action\":\"CHECK\"}", aliceToken, 400);
            JsonNode state = post(base + "/api/holdem/action",
                "{\"action\":\"CHECK\"}", bobToken, 200);
            JsonNode flop = await(aliceProbe, "FLOP");
            await(bobProbe, "FLOP");
            equal(3, flop.get("communityCards").size(), "action broadcasts visible flop");
            equal(2, flop.get("players").get(0).get("visibleCards").size(),
                "private cards retained after action");

            while (!"SHOWDOWN".equals(state.get("stage").asText())) {
                int actorId = state.get("actingPlayerId").asInt();
                String token = actorId == aliceId ? aliceToken : bobToken;
                check(actorId == aliceId || actorId == bobId, "acting player is a joined client");
                state = post(base + "/api/holdem/action", "{\"action\":\"CHECK\"}", token, 200);
            }

            equal(5, state.get("communityCards").size(), "complete hand reaches five-card board");
            check(!state.get("winnerPlayerIds").isEmpty(), "showdown exposes at least one winner");
            long totalStacks = 0;
            for (JsonNode player : state.get("players")) totalStacks += player.get("stack").asLong();
            equal(200L, totalStacks, "transport hand payout conserves all chips");
            JsonNode aliceShowdown = await(aliceProbe, "SHOWDOWN");
            JsonNode bobShowdown = await(bobProbe, "SHOWDOWN");
            equal(2, aliceShowdown.get("players").get(1).get("visibleCards").size(),
                "non-folded opponent cards reveal at showdown");
            equal(2, bobShowdown.get("players").get(0).get("visibleCards").size(),
                "both private clients receive showdown reveal");

            aliceProbe.close();
            bobProbe.close();
        }
        System.out.println("TexasHoldemTransportTest passed: " + assertions + " assertions");
    }

    private static boolean contains(JsonNode array, String expected) {
        for (JsonNode value : array) if (expected.equals(value.asText())) return true;
        return false;
    }

    private static Probe connect(PokerHttpServer server, String token) {
        LinkedBlockingQueue<JsonNode> queue = new LinkedBlockingQueue<>();
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
            .buildAsync(URI.create("ws://127.0.0.1:" + server.getPort()
                + "/ws/player?token=" + token), new Listener(queue)).join();
        return new Probe(socket, queue);
    }

    private static JsonNode await(Probe probe, String stage) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            JsonNode state = probe.queue.poll(200, TimeUnit.MILLISECONDS);
            if (state != null && state.has("stage") && stage.equals(state.get("stage").asText())) {
                return state;
            }
        }
        throw new AssertionError("missing " + stage);
    }

    private static JsonNode post(String url, String body, String token, int status)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request.build(), HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == status,
            "HTTP status " + status + " got " + response.statusCode() + " body " + response.body());
        return JSON.readTree(response.body());
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual, String message) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": " + actual);
        }
    }

    private record Probe(WebSocket socket, LinkedBlockingQueue<JsonNode> queue) {
        void close() { socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join(); }
    }

    private static final class Listener implements WebSocket.Listener {
        private final LinkedBlockingQueue<JsonNode> queue;
        private final StringBuilder message = new StringBuilder();

        Listener(LinkedBlockingQueue<JsonNode> queue) { this.queue = queue; }
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override
        public java.util.concurrent.CompletionStage<?> onText(
                WebSocket socket, CharSequence data, boolean last) {
            message.append(data);
            if (last) {
                try { queue.add(JSON.readTree(message.toString())); }
                catch (Exception exception) { throw new RuntimeException(exception); }
                message.setLength(0);
            }
            socket.request(1);
            return CompletableFuture.completedFuture(null);
        }
    }
}
