# Ultimate Poker

Ultimate Poker is a Java 17+ poker engine with a Swing table frontend and a local-network phone prototype. The server is authoritative: browser clients receive filtered snapshots and never send or select player IDs.

## Build

Install Maven 3.9+ and run:

```bash
mvn test-compile
```

The local transport uses Javalin 7.2.3 (embedded Jetty, WebSockets, and static-file serving). The engine and legacy Swing code have no direct dependency on Javalin.

Run the existing main-based regression suites:

```bash
mvn -q -Dexec.mainClass=PokerLogicTest -Dexec.classpathScope=test exec:java
mvn -q -Dexec.mainClass=GameEngineStateMachineTest -Dexec.classpathScope=test exec:java
mvn -q -Dexec.mainClass=HandEvaluatorRegressionTest -Dexec.classpathScope=test exec:java
mvn -q -Dexec.mainClass=GameSessionServiceTest -Dexec.classpathScope=test exec:java
mvn -q -Dexec.mainClass=PokerTransportIntegrationTest -Dexec.classpathScope=test exec:java
```

## Local multiplayer prototype

The easiest end-to-end test keeps the server and table in one process, sharing exactly one `GameSessionService`:

```bash
mvn -q -Dexec.mainClass=PokerNetworkTableApp exec:java
```

You may pass a port as the first argument; the default is 8080:

```bash
mvn -q -Dexec.mainClass=PokerNetworkTableApp -Dexec.args=8090 exec:java
```

Manual test on macOS:

1. Start `PokerNetworkTableApp`. Allow incoming Java connections if macOS asks.
2. The terminal and lobby show a URL such as `http://192.168.1.42:8080` and a six-character join code.
3. Connect the Mac and phone to the same Wi-Fi network. Disable VPN/client isolation if the phone cannot reach the Mac.
4. Open the printed URL in the phone browser, enter a display name and join code, then join.
5. Join from a second browser/device. The desktop Start Table button becomes available at two players.
6. Start the table, then use its **Next** button. The phones update over WebSockets after every authoritative service transition.
7. Verify each phone sees both of its own hole cards but only the currently public cards for other players.

The server binds to `0.0.0.0`, while the printed URL is discovered from an active private IPv4 interface. Firewall rules and Wi-Fi access-point isolation can still prevent device-to-device access.

For a headless server (for example, a Raspberry Pi), run:

```bash
mvn -q -Dexec.mainClass=PokerServerApp exec:java
```

It creates a session and prints its join code plus a private table token. The prototype host endpoints can then be driven by a trusted table client. Do not share the table token with players.

## HTTP API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/sessions` | Create a session; returns join code and host token |
| `POST` | `/api/sessions/{code}/join` | Join with `{ "displayName": "..." }`; returns a player token |
| `GET` | `/api/session` | Restore identity and private state using `Authorization: Bearer <player-token>` |
| `GET` | `/api/state/public?code=ABC123` | Read the public filtered snapshot |
| `GET` | `/api/state/player` | Read only the token owner's filtered snapshot |
| `POST` | `/api/table/start` | Start a round using the host token and money/bet JSON |
| `POST` | `/api/table/advance` | Advance one authoritative transition using the host token |
| `POST` | `/api/table/next-round` | Start the next round after showdown using the host token |

WebSockets:

- `/ws/player?token=...` pushes one player-specific snapshot. A newer connection replaces the token's previous active connection.
- `/ws/public?code=...` pushes public snapshots only.

Player tokens are random 256-bit values stored in browser `localStorage` for reconnection. The private endpoint has no target-player parameter: the server resolves session ID and player ID from the token. Join codes are discoverable lobby identifiers, not authentication secrets. All state is in memory and disappears when the process exits.

## Existing desktop applications

The previous applications remain available:

```bash
mvn -q -Dexec.mainClass=App exec:java
mvn -q -Dexec.mainClass=PokerTablePreview exec:java
```

`App` still launches the legacy `GameUI`; `PokerTablePreview` still runs its standalone simulated table. Neither was replaced by the network prototype.
