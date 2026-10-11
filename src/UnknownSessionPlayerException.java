public final class UnknownSessionPlayerException extends GameServiceException {
    public UnknownSessionPlayerException(String sessionId, int playerId) {
        super(GameServiceErrorCode.UNKNOWN_PLAYER,
            "Player " + playerId + " does not belong to session " + sessionId);
    }
}
