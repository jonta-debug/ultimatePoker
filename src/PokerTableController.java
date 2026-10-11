import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JPanel;

/** Bridges service commands/snapshots to the custom-painted table. */
public final class PokerTableController {
    private final PokerTablePanel tablePanel;
    private final AnimationManager animationManager;
    private final GameSessionService gameService;
    private final List<String> playerNames;
    private final double startingMoney;
    private final double entryBet;
    private final Runnable backAction;

    private String sessionId;
    private GameSnapshot currentSnapshot;
    private boolean animationsRunning;

    public PokerTableController(List<String> playerNames, double startingMoney,
                                double entryBet, Runnable backAction) {
        this(new GameSessionService(), playerNames, startingMoney, entryBet, backAction);
    }

    PokerTableController(GameSessionService gameService, List<String> playerNames,
                         double startingMoney, double entryBet, Runnable backAction) {
        if (gameService == null || playerNames == null) {
            throw new IllegalArgumentException("Service and player names must not be null");
        }
        this.gameService = gameService;
        this.playerNames = Collections.unmodifiableList(new ArrayList<>(playerNames));
        this.startingMoney = startingMoney;
        this.entryBet = entryBet;
        this.backAction = backAction == null ? () -> { } : backAction;
        tablePanel = new PokerTablePanel(this.playerNames.size());
        animationManager = new AnimationManager(tablePanel::repaint);
        tablePanel.setAnimationManager(animationManager);
        tablePanel.setNextAction(this::advanceStage);
        tablePanel.setNewRoundAction(this::startNewRound);
        tablePanel.setBackAction(this::handleBack);
    }

    public JPanel getView() { return tablePanel; }

    public void startNewRound() {
        animationManager.clear();
        animationsRunning = false;
        sessionId = gameService.createSession();
        for (String playerName : playerNames) {
            gameService.joinGame(new JoinGameRequest(sessionId, playerName));
        }
        currentSnapshot = gameService.startRound(
            new StartRoundCommand(sessionId, startingMoney, entryBet));
        TableViewState state = refreshView(currentSnapshot);
        playAnimations(createInitialPlayerDealAnimations(state), this::finishAnimationBatch);
    }

    public void advanceStage() {
        if (animationsRunning || currentSnapshot == null
                || currentSnapshot.getStage().isShowdown()) return;

        switch (currentSnapshot.getStage()) {
            case PREFLOP:
                dealThenRevealCommunity(GameStage.PREFLOP, GameStage.FLOP_DEALT, 0, 3);
                break;
            case FLOP:
                dealThenRevealCommunity(GameStage.FLOP, GameStage.TURN_DEALT, 3, 4);
                break;
            case TURN:
                advanceService(GameStage.TURN);
                refreshView(currentSnapshot);
                playAnimations(createCommunityBackDealAnimations(4, 5),
                    this::finishAnimationBatch);
                break;
            case RIVER_DEALT:
                advanceService(GameStage.RIVER_DEALT);
                TableViewState playerReveal = refreshView(currentSnapshot);
                playAnimations(createPlayerSecondCardFlipAnimations(playerReveal),
                    this::finishAnimationBatch);
                break;
            case PLAYER_CARDS_REVEALED:
                advanceService(GameStage.PLAYER_CARDS_REVEALED);
                TableViewState river = refreshView(currentSnapshot);
                playAnimations(createCommunityFlipAnimations(river, 4, 5),
                    this::finishAnimationBatch);
                break;
            case RIVER:
                advanceService(GameStage.RIVER);
                TableViewState showdown = refreshView(currentSnapshot);
                playAnimations(createWinnerAnimations(showdown), this::finishAnimationBatch);
                break;
            default:
                throw new IllegalStateException(
                    "Controller cannot advance transient stage " + currentSnapshot.getStage());
        }
    }

    private void dealThenRevealCommunity(GameStage dealFrom, GameStage revealFrom,
                                         int firstCardIndex, int cardCount) {
        advanceService(dealFrom);
        refreshView(currentSnapshot);
        playAnimations(createCommunityBackDealAnimations(firstCardIndex, cardCount), () -> {
            advanceService(revealFrom);
            TableViewState revealed = refreshView(currentSnapshot);
            playAnimations(createCommunityFlipAnimations(
                revealed, firstCardIndex, cardCount), this::finishAnimationBatch);
        });
    }

    private void advanceService(GameStage expectedStage) {
        currentSnapshot = gameService.advanceGame(
            new AdvanceGameCommand(sessionId, expectedStage));
    }

    private void handleBack() {
        animationManager.clear();
        animationsRunning = false;
        backAction.run();
    }

    private TableViewState refreshView(GameSnapshot snapshot) {
        TableViewState state = TableViewState.fromSnapshot(snapshot);
        tablePanel.setViewState(state);
        tablePanel.setNextEnabled(!snapshot.getStage().isShowdown());
        return state;
    }

    private List<TableAnimation> createInitialPlayerDealAnimations(TableViewState state) {
        List<TableAnimation> animations = new ArrayList<>();
        Rectangle deckBounds = tablePanel.getDeckCardBounds();
        int order = 0;
        for (int cardIndex = 0; cardIndex < 2; cardIndex++) {
            for (int playerIndex = 0; playerIndex < state.getPlayers().size(); playerIndex++) {
                Rectangle target = tablePanel.getPlayerCardBounds(playerIndex, cardIndex);
                PlayerView player = state.getPlayers().get(playerIndex);
                BufferedImage image = cardIndex < player.getVisibleCards().size()
                    ? tablePanel.getCardImageForAnimation(
                        player.getVisibleCards().get(cardIndex), target.width, target.height)
                    : tablePanel.getCardBackImageForAnimation(target.width, target.height);
                if (image == null) continue;
                animations.add(new CardAnimation(
                    image, deckBounds, target, 0.72, 1.0,
                    order % 2 == 0 ? -0.12 : 0.12, 0.0,
                    0.88f, 1.0f, 500, order * 55L,
                    AnimationSlot.playerCard(playerIndex, cardIndex)));
                order++;
            }
        }
        return animations;
    }

    private List<TableAnimation> createCommunityBackDealAnimations(int first, int end) {
        List<TableAnimation> animations = new ArrayList<>();
        Rectangle deckBounds = tablePanel.getDeckCardBounds();
        for (int index = first; index < end; index++) {
            Rectangle target = tablePanel.getCommunityCardBounds(index);
            BufferedImage image = tablePanel.getCardBackImageForAnimation(
                target.width, target.height);
            if (image == null) continue;
            animations.add(new CardAnimation(
                image, deckBounds, target, 0.76, 1.0,
                index % 2 == 0 ? -0.10 : 0.10, 0.0,
                0.90f, 1.0f, 620, (index - first) * 140L,
                AnimationSlot.communityCard(index)));
        }
        return animations;
    }

    private List<TableAnimation> createCommunityFlipAnimations(
            TableViewState state, int first, int end) {
        List<TableAnimation> animations = new ArrayList<>();
        for (int index = first; index < end; index++) {
            Rectangle target = tablePanel.getCommunityCardBounds(index);
            BufferedImage back = tablePanel.getCardBackImageForAnimation(
                target.width, target.height);
            BufferedImage face = tablePanel.getCardImageForAnimation(
                state.getCommunityCards().get(index), target.width, target.height);
            if (back == null || face == null) continue;
            animations.add(new FlipCardAnimation(
                back, face, target, 320, (index - first) * 140L,
                AnimationSlot.communityCard(index)));
        }
        return animations;
    }

    private List<TableAnimation> createPlayerSecondCardFlipAnimations(TableViewState state) {
        List<TableAnimation> animations = new ArrayList<>();
        for (int playerIndex = 0; playerIndex < state.getPlayers().size(); playerIndex++) {
            Rectangle target = tablePanel.getPlayerCardBounds(playerIndex, 1);
            BufferedImage back = tablePanel.getCardBackImageForAnimation(
                target.width, target.height);
            BufferedImage face = tablePanel.getCardImageForAnimation(
                state.getPlayers().get(playerIndex).getVisibleCards().get(1),
                target.width, target.height);
            if (back == null || face == null) continue;
            animations.add(new FlipCardAnimation(
                back, face, target, 320, playerIndex * 100L,
                AnimationSlot.playerCard(playerIndex, 1)));
        }
        return animations;
    }

    private List<TableAnimation> createWinnerAnimations(TableViewState state) {
        List<TableAnimation> animations = new ArrayList<>();
        for (int index = 0; index < state.getPlayers().size(); index++) {
            if (state.getWinnerPlayerIds().contains(state.getPlayers().get(index).getId())) {
                animations.add(new WinnerHighlightAnimation(
                    tablePanel.getPlayerBadgeBounds(index), 1_700));
            }
        }
        return animations;
    }

    private void playAnimations(List<TableAnimation> animations, Runnable onFinished) {
        animationsRunning = animations != null && !animations.isEmpty();
        tablePanel.setNextEnabled(false);
        animationManager.play(animations, () -> {
            animationsRunning = false;
            if (onFinished != null) onFinished.run();
        });
    }

    private void finishAnimationBatch() {
        tablePanel.setNextEnabled(currentSnapshot != null
            && !currentSnapshot.getStage().isShowdown());
    }
}
