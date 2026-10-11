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
    private final boolean attachedSession;
    private String sessionId;
    private GameSnapshot currentSnapshot;
    private boolean animationsRunning;

    public PokerTableController(List<String> playerNames, double startingMoney, double entryBet, Runnable backAction) {
        this(new GameSessionService(), playerNames, startingMoney, entryBet, backAction, null);
    }
    PokerTableController(GameSessionService service, List<String> names, double money, double bet, Runnable back) {
        this(service, names, money, bet, back, null);
    }
    /** Attaches the table renderer to a lobby already populated by network players. */
    public PokerTableController(GameSessionService service, String sessionId, double money, double bet, Runnable back) {
        this(service, namesFor(service, sessionId), money, bet, back, sessionId);
    }
    private PokerTableController(GameSessionService service, List<String> names, double money, double bet, Runnable back, String existingSessionId) {
        if (service == null || names == null) throw new IllegalArgumentException("Service and player names must not be null");
        if (names.size() < 2 || names.size() > 9) throw new IllegalArgumentException("The table requires 2 to 9 joined players");
        gameService = service; playerNames = Collections.unmodifiableList(new ArrayList<>(names)); startingMoney = money; entryBet = bet;
        backAction = back == null ? () -> { } : back; sessionId = existingSessionId; attachedSession = existingSessionId != null;
        tablePanel = new PokerTablePanel(playerNames.size()); animationManager = new AnimationManager(tablePanel::repaint);
        tablePanel.setAnimationManager(animationManager); tablePanel.setNextAction(this::advanceStage);
        tablePanel.setNewRoundAction(this::startNewRound); tablePanel.setBackAction(this::handleBack);
    }
    private static List<String> namesFor(GameSessionService service, String sessionId) {
        if (service == null || sessionId == null) throw new IllegalArgumentException("Service and session ID are required");
        List<String> names = new ArrayList<>(); for (PlayerRegistration p : service.getPlayers(sessionId)) names.add(p.getDisplayName()); return names;
    }
    public JPanel getView() { return tablePanel; }

    public void startNewRound() {
        animationManager.clear(); animationsRunning = false;
        if (attachedSession) {
            GameSnapshot state = gameService.getPublicState(sessionId);
            if (state.getStage() == GameStage.WAITING_FOR_PLAYERS) currentSnapshot = gameService.startRound(new StartRoundCommand(sessionId, startingMoney, entryBet));
            else if (state.getStage().isShowdown()) currentSnapshot = gameService.startNextRound(new StartNextRoundCommand(sessionId));
            else return;
        } else {
            sessionId = gameService.createSession();
            for (String name : playerNames) gameService.joinGame(new JoinGameRequest(sessionId, name));
            currentSnapshot = gameService.startRound(new StartRoundCommand(sessionId, startingMoney, entryBet));
        }
        TableViewState state = refreshView(currentSnapshot);
        playAnimations(createInitialPlayerDealAnimations(state), this::finishAnimationBatch);
    }

    public void advanceStage() {
        if (animationsRunning || currentSnapshot == null || currentSnapshot.getStage().isShowdown()) return;
        switch (currentSnapshot.getStage()) {
            case PREFLOP -> dealThenRevealCommunity(GameStage.PREFLOP, GameStage.FLOP_DEALT, 0, 3);
            case FLOP -> dealThenRevealCommunity(GameStage.FLOP, GameStage.TURN_DEALT, 3, 4);
            case TURN -> { advanceService(GameStage.TURN); refreshView(currentSnapshot); playAnimations(createCommunityBackDealAnimations(4, 5), this::finishAnimationBatch); }
            case RIVER_DEALT -> { advanceService(GameStage.RIVER_DEALT); TableViewState state = refreshView(currentSnapshot); playAnimations(createPlayerSecondCardFlipAnimations(state), this::finishAnimationBatch); }
            case PLAYER_CARDS_REVEALED -> { advanceService(GameStage.PLAYER_CARDS_REVEALED); TableViewState state = refreshView(currentSnapshot); playAnimations(createCommunityFlipAnimations(state, 4, 5), this::finishAnimationBatch); }
            case RIVER -> { advanceService(GameStage.RIVER); TableViewState state = refreshView(currentSnapshot); playAnimations(createWinnerAnimations(state), this::finishAnimationBatch); }
            default -> throw new IllegalStateException("Controller cannot advance transient stage " + currentSnapshot.getStage());
        }
    }
    private void dealThenRevealCommunity(GameStage dealFrom, GameStage revealFrom, int first, int end) {
        advanceService(dealFrom); refreshView(currentSnapshot);
        playAnimations(createCommunityBackDealAnimations(first, end), () -> { advanceService(revealFrom); TableViewState state = refreshView(currentSnapshot); playAnimations(createCommunityFlipAnimations(state, first, end), this::finishAnimationBatch); });
    }
    private void advanceService(GameStage expected) { currentSnapshot = gameService.advanceGame(new AdvanceGameCommand(sessionId, expected)); }
    private void handleBack() { animationManager.clear(); animationsRunning = false; backAction.run(); }
    private TableViewState refreshView(GameSnapshot snapshot) { TableViewState state = TableViewState.fromSnapshot(snapshot); tablePanel.setViewState(state); tablePanel.setNextEnabled(!snapshot.getStage().isShowdown()); return state; }

    private List<TableAnimation> createInitialPlayerDealAnimations(TableViewState state) {
        List<TableAnimation> result = new ArrayList<>(); Rectangle deck = tablePanel.getDeckCardBounds(); int order = 0;
        for (int cardIndex=0;cardIndex<2;cardIndex++) for (int playerIndex=0;playerIndex<state.getPlayers().size();playerIndex++) {
            Rectangle target=tablePanel.getPlayerCardBounds(playerIndex,cardIndex); PlayerView player=state.getPlayers().get(playerIndex);
            BufferedImage image=cardIndex<player.getVisibleCards().size()?tablePanel.getCardImageForAnimation(player.getVisibleCards().get(cardIndex),target.width,target.height):tablePanel.getCardBackImageForAnimation(target.width,target.height);
            if(image!=null) result.add(new CardAnimation(image,deck,target,.72,1.0,order%2==0?-.12:.12,0,.88f,1f,500,order++*55L,AnimationSlot.playerCard(playerIndex,cardIndex)));
        } return result;
    }
    private List<TableAnimation> createCommunityBackDealAnimations(int first,int end) {
        List<TableAnimation> result=new ArrayList<>(); Rectangle deck=tablePanel.getDeckCardBounds();
        for(int i=first;i<end;i++){Rectangle target=tablePanel.getCommunityCardBounds(i);BufferedImage image=tablePanel.getCardBackImageForAnimation(target.width,target.height);if(image!=null)result.add(new CardAnimation(image,deck,target,.76,1.0,i%2==0?-.10:.10,0,.90f,1f,620,(i-first)*140L,AnimationSlot.communityCard(i)));}return result;
    }
    private List<TableAnimation> createCommunityFlipAnimations(TableViewState state,int first,int end) {
        List<TableAnimation> result=new ArrayList<>();for(int i=first;i<end;i++){Rectangle target=tablePanel.getCommunityCardBounds(i);BufferedImage back=tablePanel.getCardBackImageForAnimation(target.width,target.height);BufferedImage face=tablePanel.getCardImageForAnimation(state.getCommunityCards().get(i),target.width,target.height);if(back!=null&&face!=null)result.add(new FlipCardAnimation(back,face,target,320,(i-first)*140L,AnimationSlot.communityCard(i)));}return result;
    }
    private List<TableAnimation> createPlayerSecondCardFlipAnimations(TableViewState state) {
        List<TableAnimation> result=new ArrayList<>();for(int i=0;i<state.getPlayers().size();i++){Rectangle target=tablePanel.getPlayerCardBounds(i,1);BufferedImage back=tablePanel.getCardBackImageForAnimation(target.width,target.height);BufferedImage face=tablePanel.getCardImageForAnimation(state.getPlayers().get(i).getVisibleCards().get(1),target.width,target.height);if(back!=null&&face!=null)result.add(new FlipCardAnimation(back,face,target,320,i*100L,AnimationSlot.playerCard(i,1)));}return result;
    }
    private List<TableAnimation> createWinnerAnimations(TableViewState state) { List<TableAnimation> result=new ArrayList<>();for(int i=0;i<state.getPlayers().size();i++)if(state.getWinnerPlayerIds().contains(state.getPlayers().get(i).getId()))result.add(new WinnerHighlightAnimation(tablePanel.getPlayerBadgeBounds(i),1700));return result; }
    private void playAnimations(List<TableAnimation> animations,Runnable finished){animationsRunning=animations!=null&&!animations.isEmpty();tablePanel.setNextEnabled(false);animationManager.play(animations,()->{animationsRunning=false;if(finished!=null)finished.run();});}
    private void finishAnimationBatch(){tablePanel.setNextEnabled(currentSnapshot!=null&&!currentSnapshot.getStage().isShowdown());}
}
