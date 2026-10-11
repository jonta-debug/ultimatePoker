public final class UnknownGameSessionException extends GameServiceException {
    public UnknownGameSessionException(String sessionId) {
        super(GameServiceErrorCode.UNKNOWN_SESSION,
            "Unknown game session: " + String.valueOf(sessionId));
    }
}
