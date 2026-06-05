import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Deck {
    List<Card> deck = new ArrayList<>();

    

    
    
    public void createDeck(){
        deck.clear();

        for(Suit suit: Suit.values()){
            for(Rank rank: Rank.values()){
                deck.add(new Card(suit, rank));
            }
        }
    }

    public void shuffleDeck(){
        Random random = new Random();
        for (int i = deck.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
        
            Card temp = deck.get(i);
            deck.set(i, deck.get(j));
            deck.set(j, temp);
        }
    }

    public Card getFirstCard(){
        return deck.get(0);
    }

    public Card drawCard(){
        return deck.remove(0);
    }

    public int cardsLeft(){
        return deck.size();
    }

    
}
