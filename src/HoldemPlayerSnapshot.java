import java.util.List;
import java.util.Set;

/** Immutable public/private projection of one Hold'em seat. */
public final class HoldemPlayerSnapshot implements PokerPlayerSnapshot {
    private final int playerId;
    private final String displayName;
    private final long stack;
    private final long streetContribution;
    private final long totalCommitted;
    private final List<Card> visibleCards;
    private final int hiddenCardCount;
    private final boolean folded;
    private final boolean allIn;
    private final boolean active;
    private final boolean dealer;
    private final boolean smallBlind;
    private final boolean bigBlind;
    private final boolean currentActor;
    private final Set<HoldemActionType> legalActions;
    private final long amountToCall;
    private final long minimumRaiseTo;
    private final HandResult finalHand;
    private final boolean winner;

    HoldemPlayerSnapshot(int id, String name, long stack, long street, long committed,
                         List<Card> cards, int hidden, boolean folded, boolean allIn,
                         boolean active, boolean dealer, boolean smallBlind, boolean bigBlind,
                         boolean actor, Set<HoldemActionType> legal, long call, long minRaise,
                         HandResult finalHand, boolean winner) {
        playerId=id; displayName=name; this.stack=stack; streetContribution=street;
        totalCommitted=committed; visibleCards=List.copyOf(cards); hiddenCardCount=hidden;
        this.folded=folded; this.allIn=allIn; this.active=active; this.dealer=dealer;
        this.smallBlind=smallBlind; this.bigBlind=bigBlind; currentActor=actor;
        legalActions=Set.copyOf(legal); amountToCall=call; minimumRaiseTo=minRaise;
        this.finalHand=finalHand; this.winner=winner;
    }
    @Override public int getPlayerId(){return playerId;}
    @Override public String getDisplayName(){return displayName;}
    @Override public double getStack(){return stack;}
    public long getStackChips(){return stack;}
    @Override public double getCurrentBet(){return streetContribution;}
    public long getStreetContribution(){return streetContribution;}
    public long getTotalCommitted(){return totalCommitted;}
    @Override public List<Card> getVisibleCards(){return visibleCards;}
    @Override public int getHiddenCardCount(){return hiddenCardCount;}
    public boolean isFolded(){return folded;}
    public boolean isAllIn(){return allIn;}
    public boolean isActive(){return active;}
    public boolean isDealer(){return dealer;}
    public boolean isSmallBlind(){return smallBlind;}
    public boolean isBigBlind(){return bigBlind;}
    public boolean isCurrentActor(){return currentActor;}
    public Set<HoldemActionType> getLegalActions(){return legalActions;}
    public long getAmountToCall(){return amountToCall;}
    public long getMinimumRaiseTo(){return minimumRaiseTo;}
    @Override public HandResult getFinalHand(){return finalHand;}
    @Override public boolean isWinner(){return winner;}
}
