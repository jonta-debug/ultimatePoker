public enum HoldemStreet {
    WAITING_FOR_PLAYERS,
    PREFLOP,
    FLOP,
    TURN,
    RIVER,
    SHOWDOWN;

    public boolean isComplete() { return this == SHOWDOWN; }
}
