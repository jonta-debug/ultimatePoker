import java.util.List;

/** Additional comparison cases retained from the pre-engine regression suite. */
public final class HandEvaluatorRegressionTest {
    private static int assertions;

    public static void main(String[] args) {
        HandEvaluator evaluator = new HandEvaluator();
        greater(evaluator, PokerLogicTest.cards("AS", "KD", "QC", "9H", "8S"),
            PokerLogicTest.cards("AH", "KS", "QD", "9C", "7S"), "high-card kicker");
        greater(evaluator, PokerLogicTest.cards("9S", "9D", "9C", "AH", "KS"),
            PokerLogicTest.cards("8S", "8D", "8C", "AH", "KS"), "trip rank");
        greater(evaluator, PokerLogicTest.cards("AS", "AD", "AC", "AH", "KS"),
            PokerLogicTest.cards("QS", "QD", "QC", "QH", "AS"), "quad rank");
        greater(evaluator, PokerLogicTest.cards("10S", "9D", "8C", "7H", "6S"),
            PokerLogicTest.cards("9H", "8D", "7C", "6H", "5S"), "straight rank");
        equal(0, evaluator.evaluate(PokerLogicTest.cards("AS", "KD", "QC", "JH", "10S"))
            .compareTo(evaluator.evaluate(
                PokerLogicTest.cards("AH", "KS", "QD", "JC", "10H"))),
            "suit does not break ties");
        System.out.println("HandEvaluatorRegressionTest passed (" + assertions + " assertions)");
    }

    private static void greater(HandEvaluator evaluator, List<Card> stronger,
                                List<Card> weaker, String message) {
        assertions++;
        if (evaluator.evaluate(stronger).compareTo(evaluator.evaluate(weaker)) <= 0) {
            throw new AssertionError(message);
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }
}
