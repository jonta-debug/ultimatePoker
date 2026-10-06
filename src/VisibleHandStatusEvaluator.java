import java.util.List;

/** Display-only hand label based exclusively on cards currently visible. */
public final class VisibleHandStatusEvaluator {
    private VisibleHandStatusEvaluator() {
    }

    public static String evaluate(List<Card> visibleCards) {
        if (visibleCards == null || visibleCards.isEmpty()) {
            return "";
        }

        HandType type = new HandEvaluator().evaluate(visibleCards).getType();
        switch (type) {
            case ONE_PAIR:
                return "Pair";
            case TWO_PAIR:
                return "Two Pair";
            case THREE_OF_A_KIND:
                return "Three of a Kind";
            case STRAIGHT:
                return "Straight";
            case FLUSH:
                return "Flush";
            case FULL_HOUSE:
                return "Full House";
            case FOUR_OF_A_KIND:
                return "Four of a Kind";
            case STRAIGHT_FLUSH:
                return "Straight Flush";
            case HIGH_CARD:
            default:
                return "High Card";
        }
    }
}
