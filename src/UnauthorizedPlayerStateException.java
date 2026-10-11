public final class UnauthorizedPlayerStateException extends GameServiceException {
    public UnauthorizedPlayerStateException(String message) {
        super(GameServiceErrorCode.UNAUTHORIZED_PRIVATE_STATE, message);
    }
}
