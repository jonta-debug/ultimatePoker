import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Player {
    private final String name;
    private final int ID;
    private double moneyAmount;
    private final List<Card> hand;
    private double currentBet;

    public Player(String name, int ID, double moneyAmount){
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Player name must not be blank");
            }
            if (ID <= 0) {
                throw new IllegalArgumentException("Player ID must be positive");
            }
            if (!Double.isFinite(moneyAmount) || moneyAmount < 0) {
                throw new IllegalArgumentException("Starting money must be finite and non-negative");
            }
            this.name = name.trim();
            this.ID = ID;
            this.moneyAmount = moneyAmount; 
            this.hand = new ArrayList<>();
            this.currentBet = 0;      
    }

    public void receiveCard(Card card){
        if (hand.size() >= 2) {
            throw new IllegalStateException("A player cannot receive more than two hole cards");
        }
        hand.add(Objects.requireNonNull(card, "card"));
    }

    public void clearHand(){
        hand.clear();
    }
    public boolean validBet(double bet){
        return Double.isFinite(bet) && bet >= 0 && bet <= moneyAmount;
    }

    public void setBet(double bet){
        if (!validBet(bet)) {
            throw new IllegalArgumentException(
                "Bet must be finite, non-negative, and no greater than the player's money");
        }
        currentBet += bet;
        moneyAmount -= bet;
    }

    public double getMoneyAmount(){
        return moneyAmount;
    }

    public List<Card> getHand(){
        return Collections.unmodifiableList(hand);
    }

    public double getCurrentBet(){
        return currentBet;
    }

    public int getID(){
        return ID;
    }

    public String getName(){
        return name;
    }
}
