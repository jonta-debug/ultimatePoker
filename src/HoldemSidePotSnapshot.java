import java.util.List;

public record HoldemSidePotSnapshot(
    long amount, List<Integer> eligiblePlayerIds, List<Integer> winnerPlayerIds) {
    public HoldemSidePotSnapshot {
        eligiblePlayerIds = List.copyOf(eligiblePlayerIds);
        winnerPlayerIds = List.copyOf(winnerPlayerIds);
    }
}
