import java.util.ArrayList;
import java.util.List;

public class Player {
    private String name;
    private int ID;
    private double moneyAmount;
    private List<Card> hand;
    private double currentBet;

    public Player(String name, int ID, double moneyAmount){
            this.name = name;
            this.ID = ID;
            this.moneyAmount = moneyAmount; 
            this.hand = new ArrayList<>();
            this.currentBet = 0;      
    }

    public void receiveCard(Card card){
        hand.add(card);
    }

    public void clearHand(){
        hand.clear();
    }
    public boolean validBet(double bet){
        return bet <= moneyAmount;
    }

    public void setBet(double bet){
        if(validBet(bet)){
            currentBet += bet;
            moneyAmount -= bet;
        }
        else{
            System.out.println("|Bet: " + bet + "is not valid|");
            System.out.println("|moneyAmonunt: " + moneyAmount + "|");
        }
        
    }

    public double getMoneyAmount(){
        return moneyAmount;
    }

    public List<Card> getHand(){
        return hand;
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
