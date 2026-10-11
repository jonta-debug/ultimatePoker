public record StartHoldemRoundCommand(
    String sessionId, long startingStack, long smallBlind, long bigBlind) { }
