import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable, fully comparable result of a poker-hand evaluation. */
public final class HandResult implements Comparable<HandResult> {
    private final HandType type;
    private final List<Integer> tiebreaker;

    public HandResult(HandType type, List<Integer> tiebreaker) {
        this.type = Objects.requireNonNull(type, "type");
        this.tiebreaker = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(tiebreaker, "tiebreaker")));
    }

    public HandType getType() {
        return type;
    }

    public List<Integer> getTiebreaker() {
        return tiebreaker;
    }

    @Override
    public int compareTo(HandResult other) {
        Objects.requireNonNull(other, "other");
        int typeComparison = Integer.compare(type.ordinal(), other.type.ordinal());
        if (typeComparison != 0) {
            return typeComparison;
        }

        int sharedSize = Math.min(tiebreaker.size(), other.tiebreaker.size());
        for (int index = 0; index < sharedSize; index++) {
            int comparison = Integer.compare(tiebreaker.get(index), other.tiebreaker.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(tiebreaker.size(), other.tiebreaker.size());
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof HandResult)) {
            return false;
        }
        HandResult other = (HandResult) object;
        return type == other.type && tiebreaker.equals(other.tiebreaker);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, tiebreaker);
    }

    @Override
    public String toString() {
        return type + " " + tiebreaker;
    }
}
