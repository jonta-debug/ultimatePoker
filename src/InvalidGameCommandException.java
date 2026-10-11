public class InvalidGameCommandException extends GameServiceException {
    public InvalidGameCommandException(String message) {
        super(GameServiceErrorCode.INVALID_COMMAND, message);
    }

    public InvalidGameCommandException(String message, Throwable cause) {
        super(GameServiceErrorCode.INVALID_COMMAND, message, cause);
    }
}
