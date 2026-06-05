import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HandEvaluator {

    private Map<Rank, Integer> getRankCounts(List<Card> cards){
        Map<Rank, Integer> rankCounts = new HashMap<>();

        for(Card card : cards){
            Rank rank = card.getRank();

            if(rankCounts.containsKey(rank)){
                rankCounts.put(rank, rankCounts.get(rank) + 1);
            } else {
                rankCounts.put(rank, 1);
            }
        }
        return rankCounts;
    }


    private Map<Suit, Integer> getSuitCounts(List<Card> cards){
        Map<Suit, Integer> suitCounts = new HashMap<>();
    
        for(Card card : cards){
            Suit suit = card.getSuit();
    
            if(suitCounts.containsKey(suit)){
                suitCounts.put(suit, suitCounts.get(suit) + 1);
            } else {
                suitCounts.put(suit, 1);
            }
        }
        return suitCounts;
    }

    public HandResult evaluate(List<Card> cards){
        if(isStraightFlush(cards)){
            return buildResult(HandType.STRAIGHT_FLUSH);
        }
        if(isFourOfAKind(cards)){
            return buildResult(HandType.FOUR_OF_A_KIND);
        }
        if(isFullHouse(cards)){
            return buildResult(HandType.FULL_HOUSE);
        }
        if(isFlush(cards)){
            return buildResult(HandType.FLUSH);
        }
        if(isStraight(cards)){
            return buildResult(HandType.STRAIGHT);
        }
        if(isThreeOfAKind(cards)){
            return buildResult(HandType.THREE_OF_A_KIND);
        }
        if(isTwoPair(cards)){
            return buildTwoPairResult(cards);
        }
        if(isOnePair(cards)){
            return buildResult(HandType.ONE_PAIR);
        }
    
        return buildResult(HandType.HIGH_CARD);
    }

    private HandResult buildResult(HandType type){
        return new HandResult(type, List.of());
    }

    private boolean isStraightFlush(List<Card> cards){
        for(Suit suit : Suit.values()){

            List<Card> suitedCards = new ArrayList<>();
        
            for(Card card : cards){
        
                if(card.getSuit() == suit){
        
                    suitedCards.add(card);
        
                }
        
            }
        
            if(suitedCards.size() >= 5){
        
                if(isStraight(suitedCards)){
        
                    return true;
        
                }
        
            }
        
        }
        
        return false;
    }

    private boolean isFourOfAKind(List<Card> cards){
        Map<Rank, Integer> counts = getRankCounts(cards);
        boolean hasFour = false;

        for(int count : counts.values()){
            if(count == 4){
                hasFour = true;
            }
        }
        return hasFour;

    }

    private boolean isFullHouse(List<Card> cards){
        Map<Rank, Integer> counts = getRankCounts(cards);
    
        int threeCount = 0;
        int pairCount = 0;
    
        for(int count : counts.values()){
            if(count >= 3){
                threeCount++;
            } else if(count >= 2){
                pairCount++;
            }
        }
    
        return threeCount >= 2 || (threeCount == 1 && pairCount >= 1);
    }

    private boolean isFlush(List<Card> cards){
        Map<Suit, Integer> counts = getSuitCounts(cards);

        boolean hasFlush = false;
        for(int count : counts.values()){
            if(count >= 5){
                hasFlush = true;
            }
        }
        return hasFlush;
    }

    private boolean isStraight(List<Card> cards){
        List<Integer> values = new ArrayList<>();
    
        for(Card card : cards){
            int value = card.getRank().getValue();
    
            if(!values.contains(value)){
                values.add(value);
            }
        }
    
        // Ace kann auch 1 zählen: A 2 3 4 5
        if(values.contains(14)){
            values.add(1);
        }
    
        Collections.sort(values);
    
        int consecutive = 1;
    
        for(int i = 1; i < values.size(); i++){
            if(values.get(i) == values.get(i - 1) + 1){
                consecutive++;
    
                if(consecutive >= 5){
                    return true;
                }
            } else {
                consecutive = 1;
            }
        }
    
        return false;
    }

    private boolean isThreeOfAKind(List<Card> cards){
        Map<Rank, Integer> counts = getRankCounts(cards);
        boolean hasThree = false;

        for(int count : counts.values()){
            if(count == 3){
                hasThree = true;
            }
        }
        return hasThree;

    }


    private boolean isTwoPair(List<Card> cards){
        Map<Rank, Integer> counts = getRankCounts(cards);
        int pairCount = 0;

        for(int count : counts.values()){
            if(count == 2){
                pairCount++;
            }
        }
        return pairCount >= 2;
    }

    private boolean isOnePair(List<Card> cards){
        Map<Rank, Integer> counts = getRankCounts(cards);
        boolean hasPair = false;

        for(int count : counts.values()){
            if(count == 2){
                hasPair = true;
            }
        }
        return hasPair;
    }

    private HandResult buildTwoPairResult(List<Card> cards){

        Map<Rank, Integer> counts = getRankCounts(cards);
    
        List<Integer> pairs = new ArrayList<>();
    
        List<Integer> kickers = new ArrayList<>();
    
        for(Rank rank : counts.keySet()){
    
            int value = rank.getValue();
    
            if(counts.get(rank) >= 2){
    
                pairs.add(value);
    
            } else {
    
                kickers.add(value);
    
            }
    
        }
    
        Collections.sort(pairs, Collections.reverseOrder());
    
        Collections.sort(kickers, Collections.reverseOrder());
    
        return new HandResult(
    
            HandType.TWO_PAIR,
    
            List.of(pairs.get(0), pairs.get(1), kickers.get(0))
    
        );
    
    }
}
