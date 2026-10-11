import java.util.ArrayList;
import java.util.List;

/** Deterministic Hold'em state-machine, betting, payout, and privacy tests. */
public final class TexasHoldemGameTest {
    private static int assertions;
    public static void main(String[] args) {
        testHeadsUpFlowAndDealerRotation();
        testThreePlayerOrderAndValidation();
        testBetRaiseFoldAndEarlyWin();
        testMultipleSidePots();
        testShortAllInDoesNotReopenAction();
        testTieAndOddChipRule();
        testPrivacyAndServiceModes();
        System.out.println("TexasHoldemGameTest passed: "+assertions+" assertions");
    }

    private static void testHeadsUpFlowAndDealerRotation(){
        TexasHoldemGame game=start(2,100,1,2);
        HoldemSnapshot s=game.publicState();
        equal(1,s.getDealerPlayerId(),"first dealer");equal(1,s.getSmallBlindPlayerId(),"heads-up dealer is small blind");equal(2,s.getBigBlindPlayerId(),"heads-up other is big blind");equal(1,s.getActingPlayerId(),"heads-up dealer acts first preflop");
        game.act(1,HoldemActionType.CALL,0);game.act(2,HoldemActionType.CHECK,0);s=game.publicState();
        equal(HoldemStreet.FLOP,s.getStreet(),"preflop to flop");equal(2,s.getActingPlayerId(),"heads-up big blind acts first postflop");
        checkStreet(game,2,1,HoldemStreet.TURN);checkStreet(game,2,1,HoldemStreet.RIVER);game.act(2,HoldemActionType.CHECK,0);game.act(1,HoldemActionType.CHECK,0);
        s=game.publicState();equal(HoldemStreet.SHOWDOWN,s.getStreet(),"river to showdown");equal(200L,totalStacks(s),"chips conserved after payout");
        game.startNextRound();s=game.publicState();equal(2,s.getDealerPlayerId(),"dealer rotates");equal(2,s.getSmallBlindPlayerId(),"new heads-up dealer posts small blind");
    }

    private static void testThreePlayerOrderAndValidation(){
        TexasHoldemGame game=start(3,100,1,2);HoldemSnapshot s=game.publicState();
        equal(1,s.getDealerPlayerId(),"three-player dealer");equal(2,s.getSmallBlindPlayerId(),"small blind left of dealer");equal(3,s.getBigBlindPlayerId(),"big blind left of small blind");equal(1,s.getActingPlayerId(),"under-the-gun acts preflop");
        expect(InvalidGameCommandException.class,()->game.act(2,HoldemActionType.CALL,0),"out of turn rejected");
        expect(InvalidGameCommandException.class,()->game.act(1,HoldemActionType.CHECK,0),"illegal check rejected");
        expect(InvalidGameCommandException.class,()->game.act(1,HoldemActionType.RAISE,3),"below-minimum raise rejected");
        game.act(1,HoldemActionType.CALL,0);game.act(2,HoldemActionType.CALL,0);game.act(3,HoldemActionType.CHECK,0);
        s=game.publicState();equal(HoldemStreet.FLOP,s.getStreet(),"three-player flop");equal(2,s.getActingPlayerId(),"first active left of dealer acts postflop");
        game.act(2,HoldemActionType.FOLD,0);equal(3,game.publicState().getActingPlayerId(),"folded player skipped");
    }

    private static void testBetRaiseFoldAndEarlyWin(){
        TexasHoldemGame game=start(3,100,1,2);
        game.act(1,HoldemActionType.RAISE,4);equal(4L,game.publicState().getCurrentBetChips(),"legal raise sets current bet");
        game.act(2,HoldemActionType.CALL,0);game.act(3,HoldemActionType.CALL,0);
        equal(HoldemStreet.FLOP,game.getStreet(),"raise and callers complete street");
        game.act(2,HoldemActionType.BET,2);game.act(3,HoldemActionType.RAISE,4);game.act(1,HoldemActionType.FOLD,0);game.act(2,HoldemActionType.CALL,0);
        equal(HoldemStreet.TURN,game.getStreet(),"bet raise call advances");
        game.act(2,HoldemActionType.FOLD,0);
        HoldemSnapshot s=game.publicState();equal(HoldemStreet.SHOWDOWN,s.getStreet(),"all but one fold ends hand");equal(List.of(3),s.getWinnerPlayerIds(),"remaining player wins");equal(300L,totalStacks(s),"early award conserves chips");equal(4,s.getVisibleCommunityCards().size(),"early win does not deal remaining board");
    }

    private static void testMultipleSidePots(){
        TexasHoldemGame game=start(3,20,1,2);
        game.act(1,HoldemActionType.FOLD,0);game.act(2,HoldemActionType.FOLD,0);
        game.startNextRound();
        HoldemSnapshot s=game.publicState();
        // Stacks entering hand two are 20, 19, 21. All-in totals create three layers.
        equal(2,s.getActingPlayerId(),"rotated dealer acts first");
        game.act(2,HoldemActionType.ALL_IN,0);game.act(3,HoldemActionType.ALL_IN,0);game.act(1,HoldemActionType.ALL_IN,0);
        s=game.publicState();equal(HoldemStreet.SHOWDOWN,s.getStreet(),"all-ins run board to showdown");
        equal(3,s.getSidePots().size(),"main and two side-pot layers");
        equal(57L,s.getSidePots().get(0).amount(),"main pot layer");
        equal(2L,s.getSidePots().get(1).amount(),"first side pot layer");
        equal(1L,s.getSidePots().get(2).amount(),"unmatched final layer returned through side-pot award");
        equal(60L,totalStacks(s),"side-pot payout conserves chips");
    }

    private static void testShortAllInDoesNotReopenAction(){
        List<Card> deck=new ArrayList<>(List.of(
            c(Suit.CLUBS,Rank.TWO),c(Suit.CLUBS,Rank.THREE),c(Suit.SPADES,Rank.ACE),
            c(Suit.DIAMONDS,Rank.FOUR),c(Suit.DIAMONDS,Rank.FIVE),c(Suit.HEARTS,Rank.ACE),
            c(Suit.CLUBS,Rank.KING),c(Suit.DIAMONDS,Rank.QUEEN),c(Suit.SPADES,Rank.NINE),
            c(Suit.CLUBS,Rank.EIGHT),c(Suit.DIAMONDS,Rank.SEVEN)));
        TexasHoldemGame game=new TexasHoldemGame(deck);start(game,3,100,1,2);
        game.act(1,HoldemActionType.RAISE,20);game.act(2,HoldemActionType.CALL,0);game.act(3,HoldemActionType.CALL,0);
        while(game.getStreet()!=HoldemStreet.SHOWDOWN){int actor=game.publicState().getActingPlayerId();game.act(actor,HoldemActionType.CHECK,0);}
        equal(140L,stack(game.publicState(),1),"deterministic first-hand winner creates unequal stacks");
        game.startNextRound();
        game.act(2,HoldemActionType.CALL,0);game.act(3,HoldemActionType.CALL,0);game.act(1,HoldemActionType.CHECK,0);
        game.act(3,HoldemActionType.BET,50);game.act(1,HoldemActionType.CALL,0);game.act(2,HoldemActionType.ALL_IN,0);
        game.act(3,HoldemActionType.ALL_IN,0);
        HoldemPlayerSnapshot actor=game.playerState(1).getPlayers().get(0);
        check(!actor.getLegalActions().contains(HoldemActionType.RAISE),"short all-in does not restore raise action");
        check(!actor.getLegalActions().contains(HoldemActionType.ALL_IN),"all-in raise is hidden when action is not reopened");
        expect(InvalidGameCommandException.class,()->game.act(1,HoldemActionType.RAISE,130),"short all-in raise is rejected by engine");
        game.act(1,HoldemActionType.CALL,0);
        equal(HoldemStreet.SHOWDOWN,game.getStreet(),"call after short all-in completes hand");
        equal(300L,totalStacks(game.publicState()),"short all-in hand conserves chips");
    }

    private static void testTieAndOddChipRule(){
        List<Card> deck=new ArrayList<>(List.of(
            c(Suit.CLUBS,Rank.TWO),c(Suit.DIAMONDS,Rank.THREE),c(Suit.HEARTS,Rank.FOUR),
            c(Suit.CLUBS,Rank.FIVE),c(Suit.DIAMONDS,Rank.SIX),c(Suit.HEARTS,Rank.SEVEN),
            c(Suit.SPADES,Rank.TEN),c(Suit.SPADES,Rank.JACK),c(Suit.SPADES,Rank.QUEEN),c(Suit.SPADES,Rank.KING),c(Suit.SPADES,Rank.ACE)));
        TexasHoldemGame game=new TexasHoldemGame(deck);start(game,3,20,1,2);
        game.act(1,HoldemActionType.CALL,0);game.act(2,HoldemActionType.FOLD,0);game.act(3,HoldemActionType.CHECK,0);
        while(game.getStreet()!=HoldemStreet.SHOWDOWN){HoldemSnapshot s=game.publicState();int actor=s.getActingPlayerId();game.act(actor,HoldemActionType.CHECK,0);}
        HoldemSnapshot s=game.publicState();equal(2,s.getWinnerPlayerIds().size(),"royal board splits pot");
        equal(20L,stack(s,1),"dealer gets even share");equal(19L,stack(s,2),"folded blind loses contribution");equal(21L,stack(s,3),"odd chip goes first eligible clockwise from dealer");
    }

    private static void testPrivacyAndServiceModes(){
        GameSessionService service=new GameSessionService();String sid=service.createSession(GameMode.TEXAS_HOLDEM);
        service.joinGame(new JoinGameRequest(sid,"A"));service.joinGame(new JoinGameRequest(sid,"B"));
        service.startHoldemRound(new StartHoldemRoundCommand(sid,100,1,2));
        HoldemSnapshot pub=(HoldemSnapshot)service.getPublicPokerState(sid);HoldemSnapshot own=(HoldemSnapshot)service.getPlayerPokerState(new PlayerStateRequest(sid,1));
        equal(GameMode.TEXAS_HOLDEM,pub.getGameMode(),"session mode exposed");equal(0,pub.getPlayers().get(0).getVisibleCards().size(),"public hole cards hidden");equal(2,own.getPlayers().get(0).getVisibleCards().size(),"owner sees both cards");equal(0,own.getPlayers().get(1).getVisibleCards().size(),"owner cannot see opponent cards");
        expect(InvalidGameCommandException.class,()->service.advanceGame(new AdvanceGameCommand(sid,GameStage.PREFLOP)),"Ultimate command rejected for Hold'em");
        String ultimate=service.createSession();equal(GameMode.ULTIMATE_POKER,service.getGameMode(ultimate),"legacy session defaults to Ultimate Poker");
    }

    private static TexasHoldemGame start(int count,long stack,long sb,long bb){TexasHoldemGame g=new TexasHoldemGame(42);start(g,count,stack,sb,bb);return g;}
    private static void start(TexasHoldemGame g,int count,long stack,long sb,long bb){List<PlayerRegistration> regs=new ArrayList<>();for(int i=1;i<=count;i++)regs.add(new PlayerRegistration("test",i,"P"+i));g.start(regs,PokerGameConfig.holdem(stack,sb,bb));}
    private static void checkStreet(TexasHoldemGame g,int first,int second,HoldemStreet expected){g.act(first,HoldemActionType.CHECK,0);g.act(second,HoldemActionType.CHECK,0);equal(expected,g.getStreet(),"street transition");}
    private static Card c(Suit s,Rank r){return new Card(s,r);}
    private static long totalStacks(HoldemSnapshot s){long n=0;for(HoldemPlayerSnapshot p:s.getPlayers())n+=p.getStackChips();return n;}
    private static long stack(HoldemSnapshot s,int id){for(HoldemPlayerSnapshot p:s.getPlayers())if(p.getPlayerId()==id)return p.getStackChips();throw new AssertionError();}
    private static void check(boolean v,String m){assertions++;if(!v)throw new AssertionError(m);}
    private static void equal(Object e,Object a,String m){assertions++;if(e==null?a!=null:!e.equals(a))throw new AssertionError(m+": expected "+e+", got "+a);}
    private static void expect(Class<? extends Throwable> type,Runnable r,String m){assertions++;try{r.run();}catch(Throwable t){if(type.isInstance(t))return;throw new AssertionError(m+": "+t,t);}throw new AssertionError(m+": expected "+type.getSimpleName());}
}
