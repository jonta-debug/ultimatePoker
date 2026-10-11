import java.util.ArrayList;
import java.util.List;

/** Explicit JSON boundary types; domain objects are never serialized directly. */
public final class TransportDtos {
    private TransportDtos() { }
    public record CardDto(String rank, String suit) { }
    public record PlayerDto(int playerId, String displayName, double stack, double currentBet, List<CardDto> visibleCards, int hiddenCardCount, String finalHand, boolean winner) { }
    public record StateDto(String type, String joinCode, Integer viewerPlayerId, long roundNumber, String stage, String statusText, List<PlayerDto> players, List<CardDto> communityCards, int dealtCommunityCardCount, int hiddenCommunityCardCount, double pot, int cardsLeftInDeck, List<Integer> winnerPlayerIds) { }
    public record ErrorDto(String type, String code, String message) { }
    public record SessionDto(String joinCode, String hostToken) { }
    public record JoinDto(String playerToken, int playerId, String displayName, String joinCode) { }
    public record IdentityDto(String displayName, String joinCode, StateDto state) { }

    /** Compatibility entry used by the token-bound player transport. */
    public static StateDto state(String code, GameSnapshot snapshot) {
        Integer viewer = null;
        int publicCardCount = snapshot.getStage().getPublicHoleCardCount();
        for (PlayerSnapshot player : snapshot.getPlayers()) {
            if (player.getVisibleCards().size() > publicCardCount) { viewer = player.getPlayerId(); break; }
        }
        return build(code, viewer, snapshot);
    }
    public static StateDto publicState(String code, GameSnapshot snapshot) { return build(code, null, snapshot); }
    public static StateDto playerState(String code, int playerId, GameSnapshot snapshot) { return build(code, playerId, snapshot); }
    private static StateDto build(String code, Integer viewerPlayerId, GameSnapshot snapshot) {
        List<PlayerDto> players = new ArrayList<>();
        for (PlayerSnapshot p : snapshot.getPlayers()) {
            List<CardDto> cards = new ArrayList<>(); for (Card c : p.getVisibleCards()) cards.add(card(c));
            players.add(new PlayerDto(p.getPlayerId(), p.getDisplayName(), p.getStack(), p.getCurrentBet(), cards, p.getHiddenCardCount(), p.getFinalHand() == null ? null : p.getFinalHand().getType().name(), p.isWinner()));
        }
        List<CardDto> board = new ArrayList<>(); for (Card c : snapshot.getVisibleCommunityCards()) board.add(card(c));
        return new StateDto("state", code, viewerPlayerId, snapshot.getRoundNumber(), snapshot.getStage().name(), snapshot.getStage().getStatusText(), players, board, snapshot.getDealtCommunityCardCount(), snapshot.getHiddenCommunityCardCount(), snapshot.getPot(), snapshot.getCardsLeftInDeck(), snapshot.getWinnerPlayerIds());
    }
    private static CardDto card(Card c) { return new CardDto(c.getRank().name(), c.getSuit().name()); }
}
