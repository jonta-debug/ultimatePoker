import java.util.Objects;

/** Identifies a static card slot temporarily covered by an animation. */
public final class AnimationSlot {
    private enum Type {
        PLAYER_CARD,
        COMMUNITY_CARD
    }

    private final Type type;
    private final int firstIndex;
    private final int secondIndex;

    private AnimationSlot(Type type, int firstIndex, int secondIndex) {
        this.type = type;
        this.firstIndex = firstIndex;
        this.secondIndex = secondIndex;
    }

    public static AnimationSlot playerCard(int playerIndex, int cardIndex) {
        return new AnimationSlot(Type.PLAYER_CARD, playerIndex, cardIndex);
    }

    public static AnimationSlot communityCard(int cardIndex) {
        return new AnimationSlot(Type.COMMUNITY_CARD, cardIndex, -1);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AnimationSlot)) {
            return false;
        }
        AnimationSlot other = (AnimationSlot) object;
        return type == other.type
            && firstIndex == other.firstIndex
            && secondIndex == other.secondIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, firstIndex, secondIndex);
    }
}
