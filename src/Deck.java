import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class Deck {
    private final List<Card> deck = new ArrayList<>();
    
    public void createDeck(){
        deck.clear();

        for(Suit suit: Suit.values()){
            for(Rank rank: Rank.values()){
                deck.add(new Card(suit, rank));
            }
        }
    }

    public void shuffleDeck(){
        shuffleDeck(new Random());
    }

    void shuffleDeck(Random random){
        Objects.requireNonNull(random, "random");
        for (int i = deck.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
        
            Card temp = deck.get(i);
            deck.set(i, deck.get(j));
            deck.set(j, temp);
        }
    }

    public Card getFirstCard(){
        ensureNotEmpty();
        return deck.get(0);
    }

    public Card drawCard(){
        ensureNotEmpty();
        return deck.remove(0);
    }

    public int cardsLeft(){
        return deck.size();
    }

    public List<Card> getCards() {
        return Collections.unmodifiableList(new ArrayList<>(deck));
    }

    private void ensureNotEmpty() {
        if (deck.isEmpty()) {
            throw new IllegalStateException("Cannot draw from an empty deck");
        }
    }
}
