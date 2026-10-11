/** Base exception exposed by the transport-independent session service. */
public class GameServiceException extends RuntimeException {
    private final GameServiceErrorCode errorCode;

    public GameServiceException(GameServiceErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public GameServiceException(GameServiceErrorCode errorCode, String message,
                                Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public GameServiceErrorCode getErrorCode() {
        return errorCode;
    }
}
