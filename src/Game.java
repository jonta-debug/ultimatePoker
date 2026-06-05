import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class Game {
    private Deck deck;
    private List<Player> playerList;
    private List<Card> communityCards;
    private double pot;

    Scanner scanner = new Scanner(System.in);

    public Game(){
        this.deck = new Deck();
        this.playerList = new ArrayList<>();
        this.communityCards = new ArrayList<>();
        this.pot = 0;
    }

    public void setupRound(List<String> playerNames, double startingMoney, double entryBet){
        resetRoundState();
        registerPlayers(playerNames, startingMoney);
        deck.createDeck();
        deck.shuffleDeck();
        collectEntryBet(entryBet);
        dealCards();
        dealCommunityCards();
    }

    private void resetRoundState(){
        this.deck = new Deck();
        this.playerList.clear();
        this.communityCards.clear();
        this.pot = 0;
    }

    public void startGame(){
        registerPlayers();
        deck.createDeck();
        deck.shuffleDeck();
        collectEntryBet(1);
        showPot();
        showPlayerInfo();
        dealCards();
        showPlayers();
        dealCommunityCards();
        showCommunityCards();
        determineWinner();
        Player winner = determineWinner();
        System.out.println("Winner: " + winner.getName());
    }

    public void dealCards(){
        for(Player p : playerList){
            for(int i = 0; i < 2; i++){
                p.receiveCard(deck.drawCard());
            }
        }
    }
    public void dealCommunityCards(){
        for(int i = 0; i < 5; i++){
            communityCards.add(deck.drawCard());
        }
    }
    
    public void showPlayers(){
        for(Player p : playerList){
            System.out.println(p.getName() + ": " + p.getHand());
        }
    }

    public void registerPlayers(){
        
        System.out.println("Number of players: ");
        
        int playerAmount = scanner.nextInt();
        for(int i = 1; i <= playerAmount; i++){
            System.out.println("Player " + i + ":");
            String name = scanner.next();
            Player p = new Player(name, i, 100);
            playerList.add(p);
        }
        System.out.println("");
        System.out.println("\nRegistered Players:");
        showPlayerInfo();
    
    
    }

    public void registerPlayers(List<String> playerNames, double startingMoney){
        playerList.clear();
        int id = 1;
        for(String playerName : playerNames){
            playerList.add(new Player(playerName, id, startingMoney));
            id++;
        }
    }
    public void showPlayerInfo(){
        for(Player p : playerList){
            System.out.println(
                p.getName() + " | Money: " + p.getMoneyAmount()
            );
          
    
        }
        System.out.println("");
    
    }
    public void showCommunityCards(){
        System.out.println("");
        System.out.println("Table: " + communityCards);
    }

    public void collectEntryBet(double entryBet){
        for(Player p : playerList){
            p.setBet(entryBet);
            pot += entryBet;
        }
        
    }
    public void showPot(){
        System.out.println("Pot: " + pot);
    }

    public Player determineWinner(){
        HandEvaluator evaluator = new HandEvaluator();
    
        Player winner = null;
        HandResult bestResult = null;
    
        for(Player p : playerList){
            List<Card> allCards = new ArrayList<>();
    
            allCards.addAll(p.getHand());
            allCards.addAll(communityCards);
    
            HandResult result = evaluator.evaluate(allCards);
    
            System.out.println(p.getName() + " has " + result);
    
            if(bestResult == null || result.compareTo(bestResult) > 0){
                bestResult = result;
                winner = p;
            }
        }
    
        return winner;
    }

    public HandResult evaluatePlayer(Player player){
        List<Card> allCards = new ArrayList<>();
        allCards.addAll(player.getHand());
        allCards.addAll(communityCards);
        return new HandEvaluator().evaluate(allCards);
    }

    public List<Player> getPlayers(){
        return Collections.unmodifiableList(playerList);
    }

    public List<Card> getCommunityCards(){
        return Collections.unmodifiableList(communityCards);
    }

    public double getPot(){
        return pot;
    }

    public int getCardsLeftInDeck(){
        return deck.cardsLeft();
    }



}

    
