import java.util.ArrayList;
import java.util.List;

/** Explicit multi-mode JSON boundary types; engines are never serialized directly. */
public final class TransportDtos {
    private TransportDtos() { }
    public record CardDto(String rank,String suit){}
    public record SidePotDto(long amount,List<Integer> eligiblePlayerIds,List<Integer> winnerPlayerIds){}
    public record PlayerDto(int playerId,String displayName,double stack,double currentBet,
        List<CardDto> visibleCards,int hiddenCardCount,String finalHand,boolean winner,
        boolean folded,boolean allIn,boolean active,boolean dealer,boolean smallBlind,
        boolean bigBlind,boolean currentActor,List<String> legalActions,long amountToCall,
        long minimumRaiseTo,long totalCommitted){}
    public record StateDto(String type,String gameMode,String joinCode,Integer viewerPlayerId,
        long roundNumber,String stage,String statusText,List<PlayerDto> players,
        List<CardDto> communityCards,int dealtCommunityCardCount,int hiddenCommunityCardCount,
        double pot,int cardsLeftInDeck,List<Integer> winnerPlayerIds,Integer dealerPlayerId,
        Integer smallBlindPlayerId,Integer bigBlindPlayerId,Integer actingPlayerId,
        Long currentBet,Long minimumRaise,List<SidePotDto> sidePots){}
    public record ErrorDto(String type,String code,String message){}
    public record SessionDto(String joinCode,String hostToken,String gameMode){}
    public record JoinDto(String playerToken,int playerId,String displayName,String joinCode,String gameMode){}
    public record IdentityDto(String displayName,String joinCode,StateDto state){}

    public static StateDto state(String code,GameSnapshot snapshot){return build(code,null,snapshot);}
    public static StateDto publicState(String code,PokerStateSnapshot snapshot){return build(code,null,snapshot);}
    public static StateDto playerState(String code,int playerId,PokerStateSnapshot snapshot){return build(code,playerId,snapshot);}
    private static StateDto build(String code,Integer viewer,PokerStateSnapshot snapshot){
        List<PlayerDto> players=new ArrayList<>();
        for(PokerPlayerSnapshot p:snapshot.getPokerPlayers()){
            List<CardDto> cards=new ArrayList<>();for(Card c:p.getVisibleCards())cards.add(card(c));
            boolean folded=false,allIn=false,active=true,dealer=false,sb=false,bb=false,actor=false;
            List<String> legal=List.of();long call=0,minRaiseTo=0,committed=0;
            if(p instanceof HoldemPlayerSnapshot h){folded=h.isFolded();allIn=h.isAllIn();active=h.isActive();dealer=h.isDealer();sb=h.isSmallBlind();bb=h.isBigBlind();actor=h.isCurrentActor();legal=h.getLegalActions().stream().map(Enum::name).sorted().toList();call=h.getAmountToCall();minRaiseTo=h.getMinimumRaiseTo();committed=h.getTotalCommitted();}
            players.add(new PlayerDto(p.getPlayerId(),p.getDisplayName(),p.getStack(),p.getCurrentBet(),cards,p.getHiddenCardCount(),p.getFinalHand()==null?null:p.getFinalHand().getType().name(),p.isWinner(),folded,allIn,active,dealer,sb,bb,actor,legal,call,minRaiseTo,committed));
        }
        List<CardDto> board=new ArrayList<>();for(Card c:snapshot.getVisibleCommunityCards())board.add(card(c));
        Integer dealer=null,sb=null,bb=null,actor=null;Long currentBet=null,minRaise=null;List<SidePotDto> pots=List.of();
        if(snapshot instanceof HoldemSnapshot h){dealer=h.getDealerPlayerId();sb=h.getSmallBlindPlayerId();bb=h.getBigBlindPlayerId();actor=h.getActingPlayerId();currentBet=h.getCurrentBetChips();minRaise=h.getMinimumRaise();pots=h.getSidePots().stream().map(p->new SidePotDto(p.amount(),p.eligiblePlayerIds(),p.winnerPlayerIds())).toList();}
        return new StateDto("state",snapshot.getGameMode().name(),code,viewer,snapshot.getRoundNumber(),snapshot.getPhaseName(),snapshot.getStatusText(),players,board,snapshot.getDealtCommunityCardCount(),snapshot.getHiddenCommunityCardCount(),snapshot.getPot(),snapshot.getCardsLeftInDeck(),snapshot.getWinnerPlayerIds(),dealer,sb,bb,actor,currentBet,minRaise,pots);
    }
    private static CardDto card(Card c){return new CardDto(c.getRank().name(),c.getSuit().name());}
}
