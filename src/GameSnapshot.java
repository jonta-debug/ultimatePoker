import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, audience-filtered Ultimate Poker state. */
public final class GameSnapshot implements PokerStateSnapshot {
    private final long roundNumber;
    private final GameStage stage;
    private final List<PlayerSnapshot> players;
    private final List<Card> visibleCommunityCards;
    private final int dealtCommunityCardCount;
    private final double pot;
    private final int cardsLeftInDeck;
    private final List<Integer> winnerPlayerIds;

    GameSnapshot(long roundNumber, GameStage stage, List<PlayerSnapshot> players,
                 List<Card> visibleCommunityCards, int dealtCommunityCardCount,
                 double pot, int cardsLeftInDeck, List<Integer> winnerPlayerIds) {
        this.roundNumber=roundNumber;this.stage=stage;
        this.players=Collections.unmodifiableList(new ArrayList<>(players));
        this.visibleCommunityCards=Collections.unmodifiableList(new ArrayList<>(visibleCommunityCards));
        this.dealtCommunityCardCount=dealtCommunityCardCount;this.pot=pot;
        this.cardsLeftInDeck=cardsLeftInDeck;
        this.winnerPlayerIds=Collections.unmodifiableList(new ArrayList<>(winnerPlayerIds));
    }
    @Override public GameMode getGameMode(){return GameMode.ULTIMATE_POKER;}
    @Override public long getRoundNumber(){return roundNumber;}
    public GameStage getStage(){return stage;}
    @Override public String getPhaseName(){return stage.name();}
    @Override public String getStatusText(){return stage.getStatusText();}
    public List<PlayerSnapshot> getPlayers(){return players;}
    @Override public List<PlayerSnapshot> getPokerPlayers(){return players;}
    @Override public List<Card> getVisibleCommunityCards(){return visibleCommunityCards;}
    @Override public int getDealtCommunityCardCount(){return dealtCommunityCardCount;}
    @Override public int getHiddenCommunityCardCount(){return dealtCommunityCardCount-visibleCommunityCards.size();}
    @Override public double getPot(){return pot;}
    @Override public int getCardsLeftInDeck(){return cardsLeftInDeck;}
    @Override public List<Integer> getWinnerPlayerIds(){return winnerPlayerIds;}
    @Override public boolean isHandComplete(){return stage.isShowdown();}
    public PlayerSnapshot getPlayer(int id){for(PlayerSnapshot p:players)if(p.getPlayerId()==id)return p;throw new IllegalArgumentException("Unknown player ID: "+id);}
}
