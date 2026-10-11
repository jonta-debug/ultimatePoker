import java.util.ArrayList;
import java.util.List;

/** Compatibility adapter that leaves the existing Ultimate Poker Game unchanged. */
final class UltimatePokerGameEngine implements PokerGameEngine {
    private final Game game = new Game();

    @Override public GameMode getMode() { return GameMode.ULTIMATE_POKER; }

    @Override
    public void start(List<PlayerRegistration> registrations, PokerGameConfig config) {
        List<String> names = new ArrayList<>();
        for (PlayerRegistration registration : registrations) names.add(registration.getDisplayName());
        game.startRound(names, config.getUltimateStartingMoney(), config.getUltimateEntryBet());
    }

    @Override public void startNextRound() { game.startNextRound(); }
    public void advance() { game.advanceGame(); }
    public GameStage getStage() { return game.getStage(); }
    @Override public GameSnapshot publicState() { return game.getPublicSnapshot(); }
    @Override public GameSnapshot playerState(int playerId) { return game.getSnapshotForPlayer(playerId); }
}
