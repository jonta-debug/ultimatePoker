import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** End-to-end HTTP/WebSocket privacy, card fidelity, and reconnect checks. */
public final class PokerTransportIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static int assertions;

    public static void main(String[] args) throws Exception {
        testExactCardTransportMapping();
        testLivePrivateAndPublicWebSockets();
        System.out.println(
            "PokerTransportIntegrationTest passed: " + assertions + " assertions");
    }

    private static void testExactCardTransportMapping() throws Exception {
        List<Card> board = List.of(
            new Card(Suit.SPADES, Rank.KING),
            new Card(Suit.HEARTS, Rank.THREE),
            new Card(Suit.DIAMONDS, Rank.TEN));
        PlayerSnapshot player = new PlayerSnapshot(
            1, "Card Check", 20, 1,
            List.of(new Card(Suit.CLUBS, Rank.ACE)), 1, null, false);
        GameSnapshot snapshot = new GameSnapshot(
            1, GameStage.FLOP, List.of(player), board, 3, 2, 44, List.of());

        JsonNode json = JSON.readTree(JSON.writeValueAsString(
            TransportDtos.publicState("ABC123", snapshot)));
        JsonNode cards = json.get("communityCards");
        equal("KING", cards.get(0).get("rank").asText(), "KING rank preserved");
        equal("SPADES", cards.get(0).get("suit").asText(), "SPADES suit preserved");
        equal("THREE", cards.get(1).get("rank").asText(), "THREE rank preserved");
        equal("HEARTS", cards.get(1).get("suit").asText(), "HEARTS suit preserved");
        equal("TEN", cards.get(2).get("rank").asText(), "TEN rank preserved");
        equal("DIAMONDS", cards.get(2).get("suit").asText(), "DIAMONDS suit preserved");
        check(!cards.get(1).get("rank").asText().equals(
            cards.get(2).get("rank").asText()), "THREE and TEN remain distinct");
    }

    private static void testLivePrivateAndPublicWebSockets() throws Exception {
        GameSessionService service = new GameSessionService();
        try (PokerHttpServer server = new PokerHttpServer(
                service, new SessionDirectory()).start("127.0.0.1", 0)) {
            String base = "http://127.0.0.1:" + server.getPort();
            JsonNode created = post(base + "/api/sessions", "{}", null, 201);
            String code = created.get("joinCode").asText();
            String hostToken = created.get("hostToken").asText();
            check(code.length() == 6, "session exposes a short code");
            check(!hostToken.isBlank(), "session returns a table token");

            post(base + "/api/sessions/BAD999/join",
                "{\"displayName\":\"Nope\"}", null, 404);
            JsonNode aliceJoin = post(base + "/api/sessions/" + code + "/join",
                "{\"displayName\":\"Alice\"}", null, 201);
            JsonNode bobJoin = post(base + "/api/sessions/" + code + "/join",
                "{\"displayName\":\"Bob\"}", null, 201);
            String aliceToken = aliceJoin.get("playerToken").asText();
            String bobToken = bobJoin.get("playerToken").asText();
            check(!aliceToken.equals(bobToken),
                "players receive different opaque tokens");

            SocketProbe alice = connectPlayer(server, aliceToken);
            SocketProbe bob = connectPlayer(server, bobToken);
            SocketProbe table = connectPublic(server, code);
            equal("WAITING_FOR_PLAYERS", awaitStage(alice, "WAITING_FOR_PLAYERS")
                .get("stage").asText(), "player socket connects before round");
            awaitStage(bob, "WAITING_FOR_PLAYERS");
            awaitStage(table, "WAITING_FOR_PLAYERS");

            post(base + "/api/table/start",
                "{\"startingMoney\":20,\"entryBet\":1}", hostToken, 200);
            JsonNode alicePreflop = awaitStage(alice, "PREFLOP");
            JsonNode bobPreflop = awaitStage(bob, "PREFLOP");
            JsonNode publicPreflop = awaitStage(table, "PREFLOP");
            check(alicePreflop.get("players").get(0)
                .get("visibleCards").size() == 2, "Alice receives both Alice cards");
            check(alicePreflop.get("players").get(1)
                .get("visibleCards").size() == 1, "Alice cannot see Bob's private card");
            check(bobPreflop.get("players").get(1)
                .get("visibleCards").size() == 2, "Bob receives both Bob cards");
            check(bobPreflop.get("players").get(0)
                .get("visibleCards").size() == 1, "Bob cannot see Alice's private card");
            check(!alicePreflop.equals(bobPreflop),
                "connected players receive different private snapshots");
            assertPublicPrivateCardsHidden(publicPreflop);

            JsonNode tampered = get(
                base + "/api/state/player?playerId=2", aliceToken, 200);
            check(tampered.get("viewerPlayerId").asInt() == 1,
                "client-supplied player ID cannot change token identity");
            get(base + "/api/state/player", "not-a-real-token", 401);

            // Deal then reveal the flop. The phone receives both pushes without HTTP reads.
            post(base + "/api/table/advance", "{}", hostToken, 200);
            JsonNode hiddenFlop = awaitStage(alice, "FLOP_DEALT");
            check(hiddenFlop.get("communityCards").isEmpty(),
                "dealt flop stays hidden until reveal transition");
            awaitStage(bob, "FLOP_DEALT");
            awaitStage(table, "FLOP_DEALT");

            JsonNode authoritativeFlop = post(
                base + "/api/table/advance", "{}", hostToken, 200);
            JsonNode aliceFlop = awaitStage(alice, "FLOP");
            JsonNode bobFlop = awaitStage(bob, "FLOP");
            JsonNode publicFlop = awaitStage(table, "FLOP");
            check(aliceFlop.get("communityCards").size() == 3,
                "player WebSocket receives visible flop without another HTTP request");
            equal(authoritativeFlop.get("communityCards"),
                aliceFlop.get("communityCards"),
                "player WebSocket preserves authoritative flop ranks and suits");
            equal(aliceFlop.get("communityCards"), bobFlop.get("communityCards"),
                "both players receive the same authoritative public board");
            equal(authoritativeFlop.get("communityCards"),
                publicFlop.get("communityCards"),
                "public WebSocket receives the same complete board snapshot");
            assertPublicPrivateCardsHidden(publicFlop);

            alice.close();
            post(base + "/api/table/advance", "{}", hostToken, 200);
            awaitStage(bob, "TURN_DEALT");
            awaitStage(table, "TURN_DEALT");
            SocketProbe reconnectedAlice = connectPlayer(server, aliceToken);
            JsonNode latest = awaitStage(reconnectedAlice, "TURN_DEALT");
            equal("TURN_DEALT", latest.get("stage").asText(),
                "reconnect immediately receives latest authoritative state");

            JsonNode reconnectHttp = get(base + "/api/session", aliceToken, 200);
            equal("Alice", reconnectHttp.get("displayName").asText(),
                "existing token restores the same player");
            reconnectedAlice.close();
            bob.close();
            table.close();
        }
    }

    private static void assertPublicPrivateCardsHidden(JsonNode state) {
        check(state.get("viewerPlayerId").isNull(),
            "public state has no private viewer identity");
        for (JsonNode player : state.get("players")) {
            check(player.get("visibleCards").size() <= 1,
                "public state transmits no private second card");
            check(player.get("hiddenCardCount").asInt() >= 1,
                "public state records each private second card as hidden");
        }
    }

    private static SocketProbe connectPlayer(
            PokerHttpServer server, String token) {
        return connect(server, "/ws/player?token=" + token);
    }

    private static SocketProbe connectPublic(PokerHttpServer server, String code) {
        return connect(server, "/ws/public?code=" + code);
    }

    private static SocketProbe connect(PokerHttpServer server, String path) {
        LinkedBlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .buildAsync(URI.create(
                "ws://127.0.0.1:" + server.getPort() + path),
                new JsonListener(messages)).join();
        return new SocketProbe(socket, messages);
    }

    private static JsonNode awaitStage(SocketProbe probe, String stage)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            JsonNode message = probe.messages.poll(250, TimeUnit.MILLISECONDS);
            if (message != null && message.has("stage")
                    && stage.equals(message.get("stage").asText())) {
                return message;
            }
        }
        throw new AssertionError("Did not receive WebSocket stage " + stage);
    }

    private static JsonNode get(String url, String token, int expected)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET();
        if (token != null) request.header("Authorization", "Bearer " + token);
        return send(request.build(), expected);
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
        check(response.statusCode() == expected,
            request.uri() + " returned " + response.statusCode()
                + " expected " + expected);
        return JSON.readTree(response.body());
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual, String message) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                message + ": expected " + expected + ", got " + actual);
        }
    }

    private static final class SocketProbe {
        private final WebSocket socket;
        private final LinkedBlockingQueue<JsonNode> messages;
        private SocketProbe(
                WebSocket socket, LinkedBlockingQueue<JsonNode> messages) {
            this.socket = socket;
            this.messages = messages;
        }
        private void close() {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    private static final class JsonListener implements WebSocket.Listener {
        private final LinkedBlockingQueue<JsonNode> messages;
        private final StringBuilder text = new StringBuilder();
        JsonListener(LinkedBlockingQueue<JsonNode> messages) {
            this.messages = messages;
        }
        @Override
        public void onOpen(WebSocket webSocket) { webSocket.request(1); }
        @Override
        public java.util.concurrent.CompletionStage<?> onText(
                WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                try {
                    messages.add(JSON.readTree(text.toString()));
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
                text.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }
    }
}
