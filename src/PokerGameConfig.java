/** Validated session-level round configuration. */
public final class PokerGameConfig {
    private final double ultimateStartingMoney;
    private final double ultimateEntryBet;
    private final long holdemStartingStack;
    private final long smallBlind;
    private final long bigBlind;

    private PokerGameConfig(double money, double entryBet, long stack, long small, long big) {
        ultimateStartingMoney = money;
        ultimateEntryBet = entryBet;
        holdemStartingStack = stack;
        smallBlind = small;
        bigBlind = big;
    }

    public static PokerGameConfig ultimate(double money, double entryBet) {
        return new PokerGameConfig(money, entryBet, 0, 0, 0);
    }

    public static PokerGameConfig holdem(long stack, long smallBlind, long bigBlind) {
        if (stack <= 0) throw new InvalidGameCommandException("Starting stack must be positive");
        if (smallBlind <= 0 || bigBlind <= smallBlind) {
            throw new InvalidGameCommandException("Blinds must be positive and big blind must exceed small blind");
        }
        if (bigBlind > stack) throw new InvalidGameCommandException("Big blind cannot exceed starting stack");
        return new PokerGameConfig(0, 0, stack, smallBlind, bigBlind);
    }

    public double getUltimateStartingMoney() { return ultimateStartingMoney; }
    public double getUltimateEntryBet() { return ultimateEntryBet; }
    public long getHoldemStartingStack() { return holdemStartingStack; }
    public long getSmallBlind() { return smallBlind; }
    public long getBigBlind() { return bigBlind; }
}
