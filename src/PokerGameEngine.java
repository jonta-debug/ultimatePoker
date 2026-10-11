import java.util.List;

/** Mode-specific authoritative engine contract owned by one GameSession. */
interface PokerGameEngine {
    GameMode getMode();
    void start(List<PlayerRegistration> players, PokerGameConfig config);
    void startNextRound();
    PokerStateSnapshot publicState();
    PokerStateSnapshot playerState(int playerId);
}
