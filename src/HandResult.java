import java.util.List;

public class HandResult implements Comparable<HandResult> {
    private HandType type;
    private List<Integer> tiebreaker;

    public HandResult(HandType type, List<Integer> tiebreaker){
        this.type = type;
        this.tiebreaker = tiebreaker;
    }

    public HandType getType(){
        return type;
    }

    public List<Integer> getTiebreaker(){
        return tiebreaker;
    }

    @Override
    public int compareTo(HandResult other){
        if(this.type.ordinal() != other.type.ordinal()){
            return Integer.compare(this.type.ordinal(), other.type.ordinal());
        }

        for(int i = 0; i < this.tiebreaker.size(); i++){
            int comparison = Integer.compare(
                this.tiebreaker.get(i),
                other.tiebreaker.get(i)
            );

            if(comparison != 0){
                return comparison;
            }
        }

        return 0;
    }

    @Override
    public String toString(){
        return type + " " + tiebreaker;
    }
}