import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable render data for one player. This class contains no game rules. */
public final class PlayerView {
    private final int id;
    private final String name;
    private final double money;
    private final double bet;
    private final List<Card> visibleCards;
    private final int hiddenCardCount;
    private final String visibleStatusText;
    private final String finalStatusText;
    private final boolean winner;

    public PlayerView(int id, String name, double money, double bet,
                      List<Card> visibleCards, int hiddenCardCount,
                      String visibleStatusText, String finalStatusText,
                      boolean winner) {
        this.id = id;
        this.name = name;
        this.money = money;
        this.bet = bet;
        this.visibleCards = Collections.unmodifiableList(new ArrayList<>(visibleCards));
        this.hiddenCardCount = Math.max(0, hiddenCardCount);
        this.visibleStatusText = visibleStatusText == null ? "" : visibleStatusText;
        this.finalStatusText = finalStatusText == null ? "" : finalStatusText;
        this.winner = winner;
    }

    public PlayerView(int id, String name, double money, double bet, List<Card> cards,
                      String statusText, boolean winner) {
        this(id, name, money, bet, cards, Math.max(0, 2 - cards.size()),
            statusText, "", winner);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getMoney() {
        return money;
    }

    public double getBet() {
        return bet;
    }

    public List<Card> getCards() {
        return visibleCards;
    }

    public List<Card> getVisibleCards() {
        return visibleCards;
    }

    public int getHiddenCardCount() {
        return hiddenCardCount;
    }

    public String getVisibleStatusText() {
        return visibleStatusText;
    }

    public String getFinalStatusText() {
        return finalStatusText;
    }

    public String getStatusText() {
        return finalStatusText.isEmpty() ? visibleStatusText : finalStatusText;
    }

    public boolean isWinner() {
        return winner;
    }
}
