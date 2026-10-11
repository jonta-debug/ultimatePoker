# Ultimate Poker

Ultimate Poker is a Java 17+ poker engine with two local playable modes:

- `ULTIMATE_POKER` — the original staged-reveal game.
- `TEXAS_HOLDEM` — no-limit Hold'em with blinds, phone betting, all-ins, side pots, and payouts.

Both modes use the same session service, privacy-filtered snapshots, HTTP/WebSocket transport, browser client, and local table composition root. Poker rules remain in their mode-specific engines.

## Build and tests

Install Maven 3.9+ and run:

```bash
mvn test-compile
```

Run the main-based suites:

```bash
mvn -q -Dexec.mainClass=PokerLogicTest -Dexec.classpathScope=test exec:java
mvn -q -Dexec.mainClass=GameEngineStateMachineTest -Dexec.classpathScope=test exec:java
mvn -q -Dexec.mainClass=HandEvaluatorRegressionTest -Dexec.classpathScope=test exec:java
mvn -q -Dexec.mainClass=GameSessionServiceTest -Dexec.classpathScope=test exec:java
mvn -q -Dexec.mainClass=PokerTransportIntegrationTest -Dexec.classpathScope=test exec:java
mvn -q -Dexec.mainClass=TexasHoldemGameTest -Dexec.classpathScope=test exec:java
mvn -q -Dexec.mainClass=TexasHoldemTransportTest -Dexec.classpathScope=test exec:java
mvn -q -Dexec.mainClass=PlayerReconnectTransportTest -Dexec.classpathScope=test exec:java
node test/mobile-card-rendering.test.js
```

The transport uses Javalin 7.2.3 with embedded Jetty and WebSockets.

## Run Ultimate Poker

```bash
mvn -q -Dexec.mainClass=PokerNetworkTableApp -Dexec.args="8080 ULTIMATE_POKER" exec:java
```

## Run Texas Hold'em

```bash
mvn -q -Dexec.mainClass=PokerNetworkTableApp -Dexec.args="8080 TEXAS_HOLDEM" exec:java
```

Then:

1. Connect the computer and phones to the same Wi-Fi.
2. Open the printed local URL on 2–9 phones or browser tabs.
3. Join with the displayed six-character code.
4. Click **Start table** after at least two players have joined.
5. In Hold'em, the acting phone receives legal action buttons. Bet and Raise amounts mean the player's target total contribution for the current street.
6. The central display remains public-only. Each phone receives only its own hidden cards.
7. At showdown, non-folded hands are revealed and chips are paid automatically. Use **Next Hand** on the table to rotate the dealer and continue.

The first Hold'em configuration uses 100-chip stacks and 1/2 blinds. The values are passed through `StartHoldemRoundCommand` and are not hardcoded in the engine.

## Hold'em rules implemented

- Correct heads-up and 3+ dealer/blind/action order.
- Check, call, bet, raise, fold, and all-in with server-side validation.
- Minimum full-raise tracking; a short all-in does not reopen action.
- Automatic flop, turn, river, and showdown transitions.
- Immediate award when all but one player folds.
- Layered main/side pots from total committed chips.
- Folded chips remain dead money but folded players are never eligible to win.
- Tied pots split evenly. Odd chips go to eligible winners clockwise starting left of the dealer.
- No burn cards in this prototype.
- Busted seats are skipped for dealing, blinds, dealer rotation, and action.

## HTTP API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/sessions` | Create a session; optional `{ "gameMode": "TEXAS_HOLDEM" }` |
| `POST` | `/api/sessions/{code}/join` | Join and receive an opaque player token |
| `GET` | `/api/session` | Restore token identity and latest private state |
| `GET` | `/api/state/public?code=ABC123` | Public mode-specific snapshot |
| `GET` | `/api/state/player` | Token owner's private snapshot |
| `POST` | `/api/table/start` | Host starts the configured mode |
| `POST` | `/api/table/advance` | Host advances Ultimate Poker only |
| `POST` | `/api/table/next-round` | Host starts the next completed hand |
| `POST` | `/api/holdem/action` | Token-bound `{ "action": "RAISE", "amount": 20 }` |

WebSockets remain:

- `/ws/player?token=...` for separately generated private snapshots.
- `/ws/public?code=...` for public snapshots only.

The server binds to `0.0.0.0`. macOS firewall permission may be required. Tokens and sessions are in memory and intended only for the trusted-LAN prototype.

## Existing desktop applications

The legacy frontend and standalone Ultimate Poker preview remain available:

```bash
mvn -q -Dexec.mainClass=App exec:java
mvn -q -Dexec.mainClass=PokerTablePreview exec:java
```

`App` still launches the original `GameUI`; it was not replaced by the multi-game network launcher.
