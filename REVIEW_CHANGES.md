# Review of Poker Table Frontend Changes

## Summary

The new frontend is available as a separate preview and does not replace the existing application. It displays a real initialized poker round on a custom-painted oval table while keeping poker rules outside the rendering component.

The data flow is:

```text
Game -> TableViewState -> PokerTablePanel
```

`App.java` still launches `GameUI.launch()`. Run `PokerTablePreview` separately to test the new frontend.

## Added files

### `src/SeatLayout.java`

- Defines normalized seat positions for 2 through 9 players.
- Stores each badge anchor as `xRatio` and `yRatio`.
- Stores card and chip offsets that point inward toward the table.
- Keeps layout geometry separate from rendering and game logic.

### `src/PokerTablePanel.java`

- Provides the new `JPanel`-based game canvas.
- Uses `Graphics2D` and anti-aliasing for all table content.
- Draws:
  - dark gradient background;
  - oval felt table, rail, and shadow;
  - player badges;
  - player cards using the real PNG sprites;
  - community cards using the real PNG sprites;
  - pot value;
  - deck and cards-left count;
  - hand status and winner highlighting;
  - custom-painted Next, New Round, and Back controls.
- Accepts connected game data through `setViewState(TableViewState)`.
- Retains the original static placeholder mode when its view state is `null`.
- Does not call `Game`, deal cards, evaluate hands, or change round state.
- Falls back to text-card placeholders if a PNG cannot be loaded.
- Reports control clicks through `Runnable` callbacks and contains no controller logic.
- Accepts an `AnimationManager` and paints active visual overlays after the static scene.
- Exposes geometry helpers for the deck, player-card slots, community-card slots, and player badges.
- Temporarily suppresses a static card while its target slot has an active animation.

### `src/TableControlButton.java`

- Lightweight painted control that does not extend `JButton`.
- Draws rounded dark buttons with normal, hover, pressed, and disabled states.
- Stores only bounds and interaction state plus a callback.
- Supports hit testing for the panel's mouse handler.

### `src/RevealStage.java`

- Defines PREFLOP, FLOP, TURN, RIVER, and SHOWDOWN presentation stages.
- Provides each stage's visible community-card count and display label.
- Provides terminal-state and next-stage helpers without modifying `Game`.

### Animation classes

- `src/AnimationManager.java`
  - Owns active visual animations and updates them with a non-blocking Swing `Timer`.
  - Requests table repaints, removes completed effects, and supports one batch-completion callback.
- `src/TableAnimation.java`
  - Defines the visual animation contract without any game-state methods.
- `src/AnimationSlot.java`
  - Identifies player-card and community-card destinations for static-slot suppression.
- `src/CardAnimation.java`
  - Interpolates card position, scale, rotation, and alpha.
  - Draws a subtle moving shadow and the cached PNG sprite.
- `src/WinnerHighlightAnimation.java`
  - Draws a short, soft pulsing glow around the winner badge.
- `src/Easing.java`
  - Provides clamping, `easeOutCubic`, and `easeInOutCubic` helpers.

### `src/PokerAssets.java`

- Adds cached `BufferedImage` accessors for card faces and the card back.
- Caches each source sprite and each requested scaled size.
- Keeps the existing `ImageIcon` API available for the old UI.

### `src/PlayerView.java`

- Immutable render data for one player.
- Contains the player ID, name, money, bet, visible cards, status text, and winner flag.
- Does not extend a Swing component and contains no poker rules.
- Defensively copies its card list and exposes it as unmodifiable.

### `src/TableViewState.java`

- Immutable snapshot of the data required by `PokerTablePanel`.
- Contains players, community cards, pot, cards-left count, table status, and winner ID.
- Provides:

```java
TableViewState.fromGame(game, showAllCards, showWinner)
TableViewState.fromGame(
    game,
    showAllPlayerCards,
    visibleCommunityCardCount,
    showWinner,
    statusText)
```

- Reads the existing public `Game` API without modifying the model.
- Calls the existing winner and hand-evaluation methods only while building the snapshot.
- Hides player cards when `showAllCards` is `false`.
- Copies only the requested number of community cards into a staged snapshot.
- Skips winner detection and player hand evaluation until `showWinner` is true.

### `src/PokerTablePreview.java`

- Provides a separate preview entry point.
- Creates a real six-player round with Ole, Mia, Noah, Lena, Tom, and Ella.
- Uses starting money `20` and entry bet `1`.
- Owns the current `Game` and `RevealStage` through a small preview controller.
- Advances PREFLOP -> FLOP -> TURN -> RIVER -> SHOWDOWN when Next is selected.
- Disables Next at SHOWDOWN.
- Deals player cards from the deck with staggered animations at PREFLOP.
- Deals newly visible community cards from the deck at FLOP, TURN, and RIVER.
- Plays a winner-badge pulse at SHOWDOWN.
- Temporarily disables Next while an animation batch is running.
- Creates and displays a fresh test round when New Round is selected.
- Resets the reveal stage to PREFLOP for every new round.
- Disposes the preview frame when Back is selected.
- Opens a centered 1280 x 800 frame titled:

```text
Ultimate Poker - Connected Table Preview
```

## Existing code intentionally unchanged

- `src/App.java` still starts the old UI.
- `src/GameUI.java` was not changed or removed.
- `src/Game.java` and the poker rules were not changed.
- The new preview uses a Swing animation timer; the old UI remains unchanged.
- Control callbacks remain in the preview/controller rather than the table renderer.
- Animation objects remain visual-only and do not read or mutate `Game`.

## Current preview behavior

The preview starts at PREFLOP and rebuilds its view snapshot after each Next action:

```java
TableViewState.fromGame(
    game,
    true,
    stage.getVisibleCommunityCardCount(),
    stage.isShowdown(),
    stage.getStatusText())
```

Player cards remain visible throughout the preview. The board reveals 0 cards at PREFLOP, 3 at FLOP, 4 at TURN, and 5 at RIVER and SHOWDOWN. Winner detection, hand labels, and winner highlighting appear only at SHOWDOWN. `Game.determineWinner()` then prints evaluation information to the terminal; this is existing behavior.

At the start of a round, player cards animate from the deck to their seat slots with a short stagger. Stage changes update `TableViewState` immediately, then animate only the newly visible community cards over the corresponding static slots. At SHOWDOWN, a temporary glow pulses around the winner while the existing static winner styling remains in place.

Visible cards use the existing PNG sprite sheet. Player cards use a compact size at each seat, community cards use a larger cached size, and the deck uses the real card-back sprite. If an image lookup fails, the renderer falls back to the rounded text card such as `A♠` or `10♥`.

## Compile and run

Compile the complete project:

```bash
javac -d /tmp/ultimate-poker-classes src/*.java
```

Run the connected table preview:

```bash
java -cp /tmp/ultimate-poker-classes PokerTablePreview
```

Run the original UI:

```bash
java -cp /tmp/ultimate-poker-classes App
```

## Suggested next step

Optionally add small chip or spark particles after showdown, or introduce a dedicated application controller before replacing the old UI entry point.
