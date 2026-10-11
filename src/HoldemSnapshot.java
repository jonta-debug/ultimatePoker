import java.util.List;

/** Immutable Hold'em snapshot, filtered for either public or one player audience. */
public final class HoldemSnapshot implements PokerStateSnapshot {
    private final long roundNumber;
    private final HoldemStreet street;
    private final List<HoldemPlayerSnapshot> players;
    private final List<Card> communityCards;
    private final long pot;
    private final int cardsLeft;
    private final int dealerId;
    private final int smallBlindId;
    private final int bigBlindId;
    private final int actingPlayerId;
    private final long currentBet;
    private final long minimumRaise;
    private final List<Integer> winners;
    private final List<HoldemSidePotSnapshot> sidePots;

    HoldemSnapshot(long round, HoldemStreet street, List<HoldemPlayerSnapshot> players,
                   List<Card> board, long pot, int cardsLeft, int dealerId,
                   int smallBlindId, int bigBlindId, int actorId, long currentBet,
                   long minimumRaise, List<Integer> winners,
                   List<HoldemSidePotSnapshot> sidePots) {
        roundNumber=round; this.street=street; this.players=List.copyOf(players);
        communityCards=List.copyOf(board); this.pot=pot; this.cardsLeft=cardsLeft;
        this.dealerId=dealerId; this.smallBlindId=smallBlindId; this.bigBlindId=bigBlindId;
        actingPlayerId=actorId; this.currentBet=currentBet; this.minimumRaise=minimumRaise;
        this.winners=List.copyOf(winners); this.sidePots=List.copyOf(sidePots);
    }
    @Override public GameMode getGameMode(){return GameMode.TEXAS_HOLDEM;}
    @Override public long getRoundNumber(){return roundNumber;}
    public HoldemStreet getStreet(){return street;}
    @Override public String getPhaseName(){return street.name();}
    @Override public String getStatusText(){return street==HoldemStreet.SHOWDOWN?"Showdown":street.name().charAt(0)+street.name().substring(1).toLowerCase();}
    public List<HoldemPlayerSnapshot> getPlayers(){return players;}
    @Override public List<HoldemPlayerSnapshot> getPokerPlayers(){return players;}
    @Override public List<Card> getVisibleCommunityCards(){return communityCards;}
    @Override public int getDealtCommunityCardCount(){return communityCards.size();}
    @Override public int getHiddenCommunityCardCount(){return 0;}
    public long getPotChips(){return pot;}
    @Override public double getPot(){return pot;}
    @Override public int getCardsLeftInDeck(){return cardsLeft;}
    public int getDealerPlayerId(){return dealerId;}
    public int getSmallBlindPlayerId(){return smallBlindId;}
    public int getBigBlindPlayerId(){return bigBlindId;}
    public int getActingPlayerId(){return actingPlayerId;}
    public long getCurrentBetChips(){return currentBet;}
    public long getMinimumRaise(){return minimumRaise;}
    @Override public List<Integer> getWinnerPlayerIds(){return winners;}
    public List<HoldemSidePotSnapshot> getSidePots(){return sidePots;}
    @Override public boolean isHandComplete(){return street.isComplete();}
}
