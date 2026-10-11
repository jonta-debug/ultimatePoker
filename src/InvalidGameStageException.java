public final class InvalidGameStageException extends GameServiceException {
    public InvalidGameStageException(String message) {
        super(GameServiceErrorCode.INVALID_GAME_STAGE, message);
    }

    public InvalidGameStageException(String message, Throwable cause) {
        super(GameServiceErrorCode.INVALID_GAME_STAGE, message, cause);
    }
}
