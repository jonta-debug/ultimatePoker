import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Evaluates the strongest five-card poker hand available from up to seven cards. */
public final class HandEvaluator {
    private static final int MIN_COMPLETE_HAND_SIZE = 5;
    private static final int MAX_HAND_SIZE = 7;

    public HandResult evaluate(List<Card> cards) {
        validate(cards);

        if (cards.size() < MIN_COMPLETE_HAND_SIZE) {
            return evaluatePartialHand(cards);
        }

        HandResult best = null;
        int size = cards.size();
        for (int a = 0; a < size - 4; a++) {
            for (int b = a + 1; b < size - 3; b++) {
                for (int c = b + 1; c < size - 2; c++) {
                    for (int d = c + 1; d < size - 1; d++) {
                        for (int e = d + 1; e < size; e++) {
                            HandResult candidate = evaluateFive(List.of(
                                cards.get(a), cards.get(b), cards.get(c),
                                cards.get(d), cards.get(e)));
                            if (best == null || candidate.compareTo(best) > 0) {
                                best = candidate;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private void validate(List<Card> cards) {
        if (cards == null) {
            throw new IllegalArgumentException("Cards must not be null");
        }
        if (cards.isEmpty() || cards.size() > MAX_HAND_SIZE) {
            throw new IllegalArgumentException("A hand must contain between 1 and 7 cards");
        }

        Set<Card> uniqueCards = new HashSet<>();
        for (Card card : cards) {
            if (card == null) {
                throw new IllegalArgumentException("Cards must not contain null");
            }
            if (!uniqueCards.add(card)) {
                throw new IllegalArgumentException("A hand must not contain duplicate cards");
            }
        }
    }

    /** Used by the staged UI for meaningful labels before five cards are visible. */
    private HandResult evaluatePartialHand(List<Card> cards) {
        Map<Rank, Integer> counts = rankCounts(cards);
        List<Integer> fours = ranksWithCount(counts, 4);
        List<Integer> threes = ranksWithCount(counts, 3);
        List<Integer> pairs = ranksWithCount(counts, 2);

        if (!fours.isEmpty()) {
            return new HandResult(HandType.FOUR_OF_A_KIND,
                withKickers(fours.get(0), cards, 1));
        }
        if (!threes.isEmpty()) {
            return new HandResult(HandType.THREE_OF_A_KIND,
                withKickers(threes.get(0), cards, 2));
        }
        if (pairs.size() >= 2) {
            int highPair = pairs.get(0);
            int lowPair = pairs.get(1);
            List<Integer> values = new ArrayList<>(List.of(highPair, lowPair));
            appendHighestExcluding(values, cards, Set.of(highPair, lowPair), 1);
            return new HandResult(HandType.TWO_PAIR, values);
        }
        if (pairs.size() == 1) {
            return new HandResult(HandType.ONE_PAIR,
                withKickers(pairs.get(0), cards, 3));
        }
        return new HandResult(HandType.HIGH_CARD, descendingRanks(cards));
    }

    private HandResult evaluateFive(List<Card> cards) {
        Map<Rank, Integer> counts = rankCounts(cards);
        List<Integer> fours = ranksWithCount(counts, 4);
        List<Integer> threes = ranksWithCount(counts, 3);
        List<Integer> pairs = ranksWithCount(counts, 2);
        boolean flush = isFlush(cards);
        int straightHigh = straightHigh(cards);

        if (flush && straightHigh > 0) {
            return new HandResult(HandType.STRAIGHT_FLUSH, List.of(straightHigh));
        }
        if (!fours.isEmpty()) {
            return new HandResult(HandType.FOUR_OF_A_KIND,
                withKickers(fours.get(0), cards, 1));
        }
        if (!threes.isEmpty() && !pairs.isEmpty()) {
            return new HandResult(HandType.FULL_HOUSE,
                List.of(threes.get(0), pairs.get(0)));
        }
        if (flush) {
            return new HandResult(HandType.FLUSH, descendingRanks(cards));
        }
        if (straightHigh > 0) {
            return new HandResult(HandType.STRAIGHT, List.of(straightHigh));
        }
        if (!threes.isEmpty()) {
            return new HandResult(HandType.THREE_OF_A_KIND,
                withKickers(threes.get(0), cards, 2));
        }
        if (pairs.size() >= 2) {
            int highPair = pairs.get(0);
            int lowPair = pairs.get(1);
            List<Integer> values = new ArrayList<>(List.of(highPair, lowPair));
            appendHighestExcluding(values, cards, Set.of(highPair, lowPair), 1);
            return new HandResult(HandType.TWO_PAIR, values);
        }
        if (pairs.size() == 1) {
            return new HandResult(HandType.ONE_PAIR,
                withKickers(pairs.get(0), cards, 3));
        }
        return new HandResult(HandType.HIGH_CARD, descendingRanks(cards));
    }

    private Map<Rank, Integer> rankCounts(List<Card> cards) {
        Map<Rank, Integer> counts = new EnumMap<>(Rank.class);
        for (Card card : cards) {
            counts.merge(card.getRank(), 1, Integer::sum);
        }
        return counts;
    }

    private List<Integer> ranksWithCount(Map<Rank, Integer> counts, int count) {
        List<Integer> ranks = new ArrayList<>();
        for (Map.Entry<Rank, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == count) {
                ranks.add(entry.getKey().getValue());
            }
        }
        ranks.sort(Collections.reverseOrder());
        return ranks;
    }

    private boolean isFlush(List<Card> cards) {
        Suit suit = cards.get(0).getSuit();
        for (int index = 1; index < cards.size(); index++) {
            if (cards.get(index).getSuit() != suit) {
                return false;
            }
        }
        return true;
    }

    private int straightHigh(List<Card> cards) {
        Set<Integer> unique = new HashSet<>();
        for (Card card : cards) {
            unique.add(card.getRank().getValue());
        }
        if (unique.contains(Rank.ACE.getValue())) {
            unique.add(1);
        }

        List<Integer> values = new ArrayList<>(unique);
        Collections.sort(values);
        int run = 1;
        int bestHigh = 0;
        for (int index = 1; index < values.size(); index++) {
            if (values.get(index) == values.get(index - 1) + 1) {
                run++;
                if (run >= 5) {
                    bestHigh = values.get(index);
                }
            } else {
                run = 1;
            }
        }
        return bestHigh;
    }

    private List<Integer> withKickers(int madeRank, List<Card> cards, int kickerCount) {
        List<Integer> values = new ArrayList<>();
        values.add(madeRank);
        appendHighestExcluding(values, cards, Set.of(madeRank), kickerCount);
        return values;
    }

    private void appendHighestExcluding(List<Integer> target, List<Card> cards,
                                        Set<Integer> excluded, int maximum) {
        List<Integer> ranks = descendingRanks(cards);
        int added = 0;
        for (int rank : ranks) {
            if (!excluded.contains(rank) && added < maximum) {
                target.add(rank);
                added++;
            }
        }
    }

    private List<Integer> descendingRanks(List<Card> cards) {
        List<Integer> ranks = new ArrayList<>();
        for (Card card : cards) {
            ranks.add(card.getRank().getValue());
        }
        ranks.sort(Collections.reverseOrder());
        return ranks;
    }
}
