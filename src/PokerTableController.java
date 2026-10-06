import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JPanel;

/** Owns round flow for the custom-painted poker table frontend. */
public final class PokerTableController {
    private final PokerTablePanel tablePanel;
    private final AnimationManager animationManager;
    private final List<String> playerNames;
    private final double startingMoney;
    private final double entryBet;
    private final Runnable backAction;

    private Game game;
    private RevealStage stage;
    private boolean animationsRunning;

    public PokerTableController(List<String> playerNames, double startingMoney,
                                double entryBet, Runnable backAction) {
        if (playerNames == null) {
            throw new IllegalArgumentException("Player names must not be null");
        }

        this.playerNames = Collections.unmodifiableList(new ArrayList<>(playerNames));
        this.startingMoney = startingMoney;
        this.entryBet = entryBet;
        this.backAction = backAction == null ? () -> { } : backAction;
        this.tablePanel = new PokerTablePanel(this.playerNames.size());
        this.animationManager = new AnimationManager(tablePanel::repaint);

        tablePanel.setAnimationManager(animationManager);
        tablePanel.setNextAction(this::advanceStage);
        tablePanel.setNewRoundAction(this::startNewRound);
        tablePanel.setBackAction(this::handleBack);
    }

    public JPanel getView() {
        return tablePanel;
    }

    public void startNewRound() {
        animationManager.clear();
        animationsRunning = false;
        game = new Game();
        game.setupRound(playerNames, startingMoney, entryBet);
        stage = RevealStage.PREFLOP_ONE_CARD_VISIBLE;
        TableViewState state = refreshView();
        playAnimations(createInitialPlayerDealAnimations(state), this::finishAnimationBatch);
    }

    public void advanceStage() {
        if (animationsRunning || stage == null || stage.isTerminal()) {
            return;
        }

        switch (stage) {
            case PREFLOP_ONE_CARD_VISIBLE:
                dealThenRevealCommunity(
                    RevealStage.FLOP_DEAL_FACE_DOWN,
                    RevealStage.FLOP_REVEAL,
                    0,
                    3);
                break;
            case FLOP_REVEAL:
                dealThenRevealCommunity(
                    RevealStage.TURN_DEAL_FACE_DOWN,
                    RevealStage.TURN_REVEAL,
                    3,
                    4);
                break;
            case TURN_REVEAL:
                transitionWithAnimations(
                    RevealStage.RIVER_DEAL_FACE_DOWN,
                    createCommunityBackDealAnimations(4, 5),
                    this::finishAnimationBatch);
                break;
            case RIVER_DEAL_FACE_DOWN:
                transitionWithAnimations(
                    RevealStage.PLAYER_SECOND_CARD_REVEAL,
                    createPlayerSecondCardFlipAnimations(),
                    this::finishAnimationBatch);
                break;
            case PLAYER_SECOND_CARD_REVEAL:
                transitionWithAnimations(
                    RevealStage.RIVER_REVEAL,
                    createCommunityFlipAnimations(4, 5),
                    this::finishAnimationBatch);
                break;
            case RIVER_REVEAL:
                stage = RevealStage.SHOWDOWN;
                TableViewState state = refreshView();
                playAnimations(createWinnerAnimations(state), this::finishAnimationBatch);
                break;
            default:
                break;
        }
    }

    private void dealThenRevealCommunity(RevealStage dealStage, RevealStage revealStage,
                                         int firstCardIndex, int cardCount) {
        stage = dealStage;
        refreshView();
        playAnimations(createCommunityBackDealAnimations(firstCardIndex, cardCount), () -> {
            stage = revealStage;
            refreshView();
            playAnimations(createCommunityFlipAnimations(firstCardIndex, cardCount),
                this::finishAnimationBatch);
        });
    }

    private void transitionWithAnimations(RevealStage nextStage,
                                          List<TableAnimation> animations,
                                          Runnable onFinished) {
        stage = nextStage;
        refreshView();
        playAnimations(animations, onFinished);
    }

    private void handleBack() {
        animationManager.clear();
        animationsRunning = false;
        backAction.run();
    }

    private TableViewState refreshView() {
        TableViewState state = TableViewState.fromGame(
            game,
            stage.getVisiblePlayerCardCount(),
            stage.getDealtCommunityCardCount(),
            stage.getRevealedCommunityCardCount(),
            stage.shouldShowWinner(),
            stage.shouldShowFinalHandStatus(),
            stage.getStatusText());
        tablePanel.setViewState(state);
        tablePanel.setNextEnabled(!stage.isTerminal());
        return state;
    }

    private List<TableAnimation> createInitialPlayerDealAnimations(TableViewState state) {
        List<TableAnimation> animations = new ArrayList<>();
        Rectangle deckBounds = tablePanel.getDeckCardBounds();
        int dealOrder = 0;

        for (int cardIndex = 0; cardIndex < 2; cardIndex++) {
            for (int playerIndex = 0; playerIndex < state.getPlayers().size(); playerIndex++) {
                Rectangle targetBounds = tablePanel.getPlayerCardBounds(playerIndex, cardIndex);
                BufferedImage image = cardIndex == 0
                    ? tablePanel.getCardImageForAnimation(
                        game.getPlayers().get(playerIndex).getHand().get(cardIndex),
                        targetBounds.width,
                        targetBounds.height)
                    : tablePanel.getCardBackImageForAnimation(
                        targetBounds.width,
                        targetBounds.height);
                if (image == null) {
                    continue;
                }

                animations.add(new CardAnimation(
                    image,
                    deckBounds,
                    targetBounds,
                    0.72,
                    1.0,
                    dealOrder % 2 == 0 ? -0.12 : 0.12,
                    0.0,
                    0.88f,
                    1.0f,
                    500,
                    dealOrder * 55L,
                    AnimationSlot.playerCard(playerIndex, cardIndex)));
                dealOrder++;
            }
        }
        return animations;
    }

    private List<TableAnimation> createCommunityBackDealAnimations(
            int firstCardIndex, int cardCount) {
        List<TableAnimation> animations = new ArrayList<>();
        Rectangle deckBounds = tablePanel.getDeckCardBounds();

        for (int cardIndex = firstCardIndex; cardIndex < cardCount; cardIndex++) {
            Rectangle targetBounds = tablePanel.getCommunityCardBounds(cardIndex);
            BufferedImage backImage = tablePanel.getCardBackImageForAnimation(
                targetBounds.width, targetBounds.height);
            if (backImage == null) {
                continue;
            }

            animations.add(new CardAnimation(
                backImage,
                deckBounds,
                targetBounds,
                0.76,
                1.0,
                cardIndex % 2 == 0 ? -0.10 : 0.10,
                0.0,
                0.90f,
                1.0f,
                620,
                (cardIndex - firstCardIndex) * 140L,
                AnimationSlot.communityCard(cardIndex)));
        }
        return animations;
    }

    private List<TableAnimation> createCommunityFlipAnimations(
            int firstCardIndex, int cardCount) {
        List<TableAnimation> animations = new ArrayList<>();

        for (int cardIndex = firstCardIndex; cardIndex < cardCount; cardIndex++) {
            Rectangle targetBounds = tablePanel.getCommunityCardBounds(cardIndex);
            BufferedImage backImage = tablePanel.getCardBackImageForAnimation(
                targetBounds.width, targetBounds.height);
            BufferedImage faceImage = tablePanel.getCardImageForAnimation(
                game.getCommunityCards().get(cardIndex),
                targetBounds.width,
                targetBounds.height);
            if (backImage == null || faceImage == null) {
                continue;
            }

            animations.add(new FlipCardAnimation(
                backImage,
                faceImage,
                targetBounds,
                320,
                (cardIndex - firstCardIndex) * 140L,
                AnimationSlot.communityCard(cardIndex)));
        }
        return animations;
    }

    private List<TableAnimation> createPlayerSecondCardFlipAnimations() {
        List<TableAnimation> animations = new ArrayList<>();

        for (int playerIndex = 0; playerIndex < game.getPlayers().size(); playerIndex++) {
            Rectangle targetBounds = tablePanel.getPlayerCardBounds(playerIndex, 1);
            BufferedImage backImage = tablePanel.getCardBackImageForAnimation(
                targetBounds.width, targetBounds.height);
            BufferedImage faceImage = tablePanel.getCardImageForAnimation(
                game.getPlayers().get(playerIndex).getHand().get(1),
                targetBounds.width,
                targetBounds.height);
            if (backImage == null || faceImage == null) {
                continue;
            }

            animations.add(new FlipCardAnimation(
                backImage,
                faceImage,
                targetBounds,
                320,
                playerIndex * 100L,
                AnimationSlot.playerCard(playerIndex, 1)));
        }
        return animations;
    }

    private List<TableAnimation> createWinnerAnimations(TableViewState state) {
        List<TableAnimation> animations = new ArrayList<>();
        for (int playerIndex = 0; playerIndex < state.getPlayers().size(); playerIndex++) {
            if (state.getWinnerPlayerIds().contains(
                    state.getPlayers().get(playerIndex).getId())) {
                animations.add(new WinnerHighlightAnimation(
                    tablePanel.getPlayerBadgeBounds(playerIndex), 1_700));
            }
        }
        return animations;
    }

    private void playAnimations(List<TableAnimation> animations, Runnable onFinished) {
        animationsRunning = animations != null && !animations.isEmpty();
        tablePanel.setNextEnabled(false);
        animationManager.play(animations, () -> {
            animationsRunning = false;
            if (onFinished != null) {
                onFinished.run();
            }
        });
    }

    private void finishAnimationBatch() {
        tablePanel.setNextEnabled(!stage.isTerminal());
    }
}
