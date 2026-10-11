import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Authoritative no-limit Texas Hold'em engine using integer chip units. */
public final class TexasHoldemGame implements PokerGameEngine {
    private final List<HPlayer> players = new ArrayList<>();
    private final List<Card> board = new ArrayList<>();
    private final List<Integer> winnerIds = new ArrayList<>();
    private final List<HoldemSidePotSnapshot> resolvedPots = new ArrayList<>();
    private final Random random;
    private List<Card> presetDeck;
    private List<Card> deck = new ArrayList<>();
    private HoldemStreet street = HoldemStreet.WAITING_FOR_PLAYERS;
    private long roundNumber;
    private long smallBlind;
    private long bigBlind;
    private long currentBet;
    private long minimumRaise;
    private int dealerIndex = -1;
    private int smallBlindIndex = -1;
    private int bigBlindIndex = -1;
    private int actorIndex = -1;

    public TexasHoldemGame() { this(new Random(), null); }
    TexasHoldemGame(long seed) { this(new Random(seed), null); }
    TexasHoldemGame(List<Card> drawOrder) { this(new Random(0), drawOrder); }
    private TexasHoldemGame(Random random, List<Card> drawOrder) {
        this.random = random;
        this.presetDeck = drawOrder == null ? null : List.copyOf(drawOrder);
    }

    @Override public GameMode getMode() { return GameMode.TEXAS_HOLDEM; }

    @Override
    public void start(List<PlayerRegistration> registrations, PokerGameConfig config) {
        if (street != HoldemStreet.WAITING_FOR_PLAYERS) {
            throw new IllegalStateException("Hold'em hand already started");
        }
        if (registrations == null || registrations.size() < 2 || registrations.size() > 9) {
            throw new IllegalArgumentException("Hold'em requires 2 to 9 players");
        }
        smallBlind = config.getSmallBlind();
        bigBlind = config.getBigBlind();
        players.clear();
        for (PlayerRegistration registration : registrations) {
            players.add(new HPlayer(registration.getPlayerId(),
                registration.getDisplayName(), config.getHoldemStartingStack()));
        }
        beginHand(false);
    }

    @Override
    public void startNextRound() {
        if (street != HoldemStreet.SHOWDOWN) {
            throw new IllegalStateException("Next hand requires a completed Hold'em hand");
        }
        if (activeStackCount() < 2) {
            throw new IllegalStateException("At least two players with chips are required");
        }
        beginHand(true);
    }

    public void act(int playerId, HoldemActionType action, long amount) {
        if (street == HoldemStreet.WAITING_FOR_PLAYERS || street == HoldemStreet.SHOWDOWN) {
            throw new InvalidGameStageException("No Hold'em action is allowed at " + street);
        }
        if (action == null) throw new InvalidGameCommandException("Hold'em action is required");
        int index = indexOf(playerId);
        if (index != actorIndex) throw new InvalidGameCommandException("Player attempted to act out of turn");
        HPlayer player = players.get(index);
        if (!canAct(player)) throw new InvalidGameCommandException("Player cannot act");
        long toCall = Math.max(0, currentBet - player.streetContribution);

        switch (action) {
            case FOLD -> { player.folded = true; player.acted = true; }
            case CHECK -> {
                if (toCall != 0) throw new InvalidGameCommandException("Cannot check while facing a bet");
                player.acted = true;
            }
            case CALL -> {
                if (toCall == 0) throw new InvalidGameCommandException("Nothing to call");
                commit(player, Math.min(toCall, player.stack));
                player.acted = true;
            }
            case BET -> applyAggressiveAction(player, amount, true);
            case RAISE -> applyAggressiveAction(player, amount, false);
            case ALL_IN -> {
                if (player.stack == 0) throw new InvalidGameCommandException("Player is already all-in");
                applyAllIn(player);
            }
        }

        if (remainingCount() == 1) {
            awardEarlyWinner();
            return;
        }
        if (bettingRoundComplete()) {
            advanceStreet();
        } else {
            actorIndex = nextCanAct(actorIndex);
            if (actorIndex < 0) advanceStreet();
        }
    }

    @Override public HoldemSnapshot publicState() { return snapshot(null); }
    @Override public HoldemSnapshot playerState(int playerId) {
        indexOf(playerId);
        return snapshot(playerId);
    }
    public HoldemStreet getStreet() { return street; }

    private void beginHand(boolean rotateDealer) {
        prepareDeck();
        board.clear(); winnerIds.clear(); resolvedPots.clear(); currentBet = 0;
        for (HPlayer player : players) player.resetForHand();
        if (rotateDealer) dealerIndex = nextActive(dealerIndex);
        else dealerIndex = nextActive(-1);
        int active = activeStackCount();
        if (active == 2) {
            smallBlindIndex = dealerIndex;
            bigBlindIndex = nextActive(dealerIndex);
        } else {
            smallBlindIndex = nextActive(dealerIndex);
            bigBlindIndex = nextActive(smallBlindIndex);
        }
        dealHoleCards();
        postBlind(players.get(smallBlindIndex), smallBlind);
        postBlind(players.get(bigBlindIndex), bigBlind);
        currentBet = Math.max(players.get(smallBlindIndex).streetContribution,
            players.get(bigBlindIndex).streetContribution);
        minimumRaise = bigBlind;
        street = HoldemStreet.PREFLOP;
        roundNumber++;
        actorIndex = active == 2 ? dealerIndex : nextCanAct(bigBlindIndex);
        if (actorIndex < 0) runOutBoard();
    }

    private void prepareDeck() {
        if (presetDeck != null) {
            deck = new ArrayList<>(presetDeck);
            presetDeck = null;
        } else {
            deck = new ArrayList<>();
            for (Suit suit : Suit.values()) for (Rank rank : Rank.values()) deck.add(new Card(suit, rank));
            Collections.shuffle(deck, random);
        }
        if (new HashSet<>(deck).size() != deck.size()) {
            throw new IllegalArgumentException("Preset deck contains duplicate cards");
        }
        if (deck.size() < activeStackCount() * 2 + 5) {
            throw new IllegalArgumentException("Deck does not contain enough cards");
        }
    }

    private void dealHoleCards() {
        int cursor = dealerIndex;
        for (int card = 0; card < 2; card++) {
            for (int dealt = 0; dealt < activeStackCount(); dealt++) {
                cursor = nextActive(cursor);
                players.get(cursor).cards.add(draw());
            }
        }
    }

    private void postBlind(HPlayer player, long blind) {
        commit(player, Math.min(blind, player.stack));
    }

    private void applyAggressiveAction(HPlayer player, long target, boolean bet) {
        if (target <= player.streetContribution) {
            throw new InvalidGameCommandException("Bet amount must increase contribution");
        }
        if (target - player.streetContribution > player.stack) {
            throw new InvalidGameCommandException("Bet exceeds player stack");
        }
        if (bet && currentBet != 0) throw new InvalidGameCommandException("Use raise while a bet is open");
        if (!bet && currentBet == 0) throw new InvalidGameCommandException("Use bet when no bet is open");
        if (!bet && player.acted) {
            throw new InvalidGameCommandException("Betting was not reopened by the short all-in");
        }
        if (target <= currentBet) throw new InvalidGameCommandException("Raise must exceed current bet");
        long increase = target - currentBet;
        boolean allInTarget = target == player.streetContribution + player.stack;
        long required = currentBet == 0 ? bigBlind : minimumRaise;
        if (increase < required && !allInTarget) {
            throw new InvalidGameCommandException("Bet or raise is below the legal minimum");
        }
        commit(player, target - player.streetContribution);
        if (increase >= required) {
            minimumRaise = increase;
            resetActedAfterFullRaise(player);
        }
        currentBet = target;
        player.acted = true;
    }

    private void applyAllIn(HPlayer player) {
        long target = player.streetContribution + player.stack;
        if (target > currentBet && player.acted) {
            throw new InvalidGameCommandException("Betting was not reopened by the short all-in");
        }
        if (target > currentBet) {
            long increase = target - currentBet;
            long required = currentBet == 0 ? bigBlind : minimumRaise;
            commit(player, player.stack);
            if (increase >= required) {
                minimumRaise = increase;
                resetActedAfterFullRaise(player);
            }
            currentBet = target;
        } else {
            commit(player, player.stack);
        }
        player.acted = true;
    }

    private void resetActedAfterFullRaise(HPlayer aggressor) {
        for (HPlayer other : players) {
            if (other != aggressor && canAct(other)) other.acted = false;
        }
    }

    private void commit(HPlayer player, long chips) {
        if (chips < 0 || chips > player.stack) throw new InvalidGameCommandException("Invalid chip amount");
        player.stack -= chips;
        player.streetContribution += chips;
        player.totalCommitted += chips;
        if (player.stack == 0) player.allIn = true;
    }

    private boolean bettingRoundComplete() {
        for (HPlayer player : players) {
            if (!player.active || player.folded || player.allIn) continue;
            if (!player.acted || player.streetContribution != currentBet) return false;
        }
        return true;
    }

    private void advanceStreet() {
        if (remainingCount() == 1) { awardEarlyWinner(); return; }
        switch (street) {
            case PREFLOP -> { dealBoard(3); street = HoldemStreet.FLOP; }
            case FLOP -> { dealBoard(1); street = HoldemStreet.TURN; }
            case TURN -> { dealBoard(1); street = HoldemStreet.RIVER; }
            case RIVER -> { showdown(); return; }
            default -> throw new IllegalStateException("Cannot advance Hold'em street " + street);
        }
        currentBet = 0;
        minimumRaise = bigBlind;
        for (HPlayer player : players) {
            player.streetContribution = 0;
            player.acted = false;
        }
        if (playersAbleToAct() <= 1) { runOutBoard(); return; }
        actorIndex = nextCanAct(dealerIndex);
    }

    private void runOutBoard() {
        while (street != HoldemStreet.RIVER && street != HoldemStreet.SHOWDOWN) {
            if (street == HoldemStreet.PREFLOP) { dealBoard(3); street = HoldemStreet.FLOP; }
            else if (street == HoldemStreet.FLOP) { dealBoard(1); street = HoldemStreet.TURN; }
            else if (street == HoldemStreet.TURN) { dealBoard(1); street = HoldemStreet.RIVER; }
        }
        if (street == HoldemStreet.RIVER) showdown();
    }

    private void dealBoard(int count) { for (int i = 0; i < count; i++) board.add(draw()); }
    private Card draw() {
        if (deck.isEmpty()) throw new IllegalStateException("Deck exhausted");
        return deck.remove(0);
    }

    private void awardEarlyWinner() {
        HPlayer winner = null;
        for (HPlayer player : players) if (player.active && !player.folded) { winner = player; break; }
        if (winner == null) throw new IllegalStateException("No remaining player");
        long amount = pot();
        winner.stack += amount;
        winnerIds.clear(); winnerIds.add(winner.id);
        resolvedPots.clear();
        resolvedPots.add(new HoldemSidePotSnapshot(amount, List.of(winner.id), List.of(winner.id)));
        actorIndex = -1;
        street = HoldemStreet.SHOWDOWN;
    }

    private void showdown() {
        if (board.size() != 5) throw new IllegalStateException("Showdown requires five community cards");
        winnerIds.clear(); resolvedPots.clear();
        List<Long> levels = players.stream().map(p -> p.totalCommitted)
            .filter(value -> value > 0).distinct().sorted().toList();
        long previous = 0;
        for (long level : levels) {
            List<HPlayer> contributors = players.stream()
                .filter(p -> p.totalCommitted >= level).toList();
            long amount = (level - previous) * contributors.size();
            previous = level;
            List<HPlayer> eligible = contributors.stream()
                .filter(p -> p.active && !p.folded).toList();
            if (eligible.isEmpty()) continue;
            List<HPlayer> winners = bestPlayers(eligible);
            awardPot(amount, winners);
            List<Integer> eligibleIds = eligible.stream().map(p -> p.id).toList();
            List<Integer> potWinners = winners.stream().map(p -> p.id).toList();
            resolvedPots.add(new HoldemSidePotSnapshot(amount, eligibleIds, potWinners));
            for (int id : potWinners) if (!winnerIds.contains(id)) winnerIds.add(id);
        }
        actorIndex = -1;
        street = HoldemStreet.SHOWDOWN;
    }

    private List<HPlayer> bestPlayers(List<HPlayer> eligible) {
        HandEvaluator evaluator = new HandEvaluator();
        HandResult best = null;
        List<HPlayer> winners = new ArrayList<>();
        for (HPlayer player : eligible) {
            List<Card> seven = new ArrayList<>(player.cards); seven.addAll(board);
            HandResult result = evaluator.evaluate(seven);
            int comparison = best == null ? 1 : result.compareTo(best);
            if (comparison > 0) { winners.clear(); winners.add(player); best = result; }
            else if (comparison == 0) winners.add(player);
        }
        return winners;
    }

    private void awardPot(long amount, List<HPlayer> winners) {
        long share = amount / winners.size();
        long remainder = amount % winners.size();
        for (HPlayer winner : winners) winner.stack += share;
        List<HPlayer> clockwise = new ArrayList<>(winners);
        clockwise.sort(Comparator.comparingInt(p -> clockwiseDistance(p.seat)));
        for (int i = 0; i < remainder; i++) clockwise.get(i).stack++;
    }

    private int clockwiseDistance(int seat) {
        int distance = (seat - dealerIndex - 1 + players.size()) % players.size();
        return distance;
    }

    private HoldemSnapshot snapshot(Integer viewerId) {
        List<HoldemPlayerSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            HPlayer player = players.get(i);
            boolean own = viewerId != null && viewerId == player.id;
            boolean showdownVisible = street == HoldemStreet.SHOWDOWN && !player.folded;
            List<Card> visible = own || showdownVisible ? player.cards : List.of();
            int hidden = player.cards.size() - visible.size();
            HandResult result = showdownVisible ? evaluate(player) : null;
            Set<HoldemActionType> legal = own && i == actorIndex
                ? legalActions(player) : Set.of();
            snapshots.add(new HoldemPlayerSnapshot(
                player.id, player.name, player.stack, player.streetContribution,
                player.totalCommitted, visible, hidden, player.folded, player.allIn,
                player.active, i==dealerIndex, i==smallBlindIndex, i==bigBlindIndex,
                i==actorIndex, legal, Math.max(0,currentBet-player.streetContribution),
                currentBet+minimumRaise, result, winnerIds.contains(player.id)));
        }
        return new HoldemSnapshot(roundNumber, street, snapshots, board, pot(), deck.size(),
            idAt(dealerIndex), idAt(smallBlindIndex), idAt(bigBlindIndex), idAt(actorIndex),
            currentBet, minimumRaise, winnerIds, resolvedPots);
    }

    private Set<HoldemActionType> legalActions(HPlayer player) {
        if (!canAct(player) || players.get(actorIndex) != player) return Set.of();
        EnumSet<HoldemActionType> actions = EnumSet.of(HoldemActionType.FOLD);
        long call = Math.max(0, currentBet-player.streetContribution);
        if (!player.acted || player.streetContribution + player.stack <= currentBet) {
            actions.add(HoldemActionType.ALL_IN);
        }
        if (call == 0) actions.add(HoldemActionType.CHECK);
        else actions.add(HoldemActionType.CALL);
        if (currentBet == 0 && player.stack >= bigBlind) actions.add(HoldemActionType.BET);
        if (!player.acted && currentBet > 0
                && player.streetContribution+player.stack >= currentBet+minimumRaise) {
            actions.add(HoldemActionType.RAISE);
        }
        return actions;
    }

    private HandResult evaluate(HPlayer player) {
        List<Card> cards = new ArrayList<>(player.cards); cards.addAll(board);
        return new HandEvaluator().evaluate(cards);
    }
    private long pot() { long total=0; for(HPlayer p:players) total+=p.totalCommitted; return total; }
    private int remainingCount(){int n=0;for(HPlayer p:players)if(p.active&&!p.folded)n++;return n;}
    private int playersAbleToAct(){int n=0;for(HPlayer p:players)if(canAct(p))n++;return n;}
    private int activeStackCount(){int n=0;for(HPlayer p:players)if(p.stack>0)n++;return n;}
    private boolean canAct(HPlayer p){return p.active&&!p.folded&&!p.allIn&&p.stack>0;}
    private int nextCanAct(int from){for(int step=1;step<=players.size();step++){int i=(from+step+players.size())%players.size();if(canAct(players.get(i)))return i;}return -1;}
    private int nextActive(int from){for(int step=1;step<=players.size();step++){int i=(from+step+players.size())%players.size();if(players.get(i).stack>0)return i;}return -1;}
    private int indexOf(int id){for(int i=0;i<players.size();i++)if(players.get(i).id==id)return i;throw new UnknownSessionPlayerException("holdem",id);}
    private int idAt(int index){return index<0?-1:players.get(index).id;}

    private static final class HPlayer {
        final int id; final String name; final int seat; final List<Card> cards=new ArrayList<>();
        long stack; long streetContribution; long totalCommitted;
        boolean folded; boolean allIn; boolean active; boolean acted;
        HPlayer(int id,String name,long stack){this.id=id;this.name=name;this.stack=stack;this.seat=id-1;}
        void resetForHand(){cards.clear();streetContribution=0;totalCommitted=0;folded=false;allIn=false;acted=false;active=stack>0;}
    }
}
