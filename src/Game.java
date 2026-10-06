import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class Game {
    private Deck deck;
    private List<Player> playerList;
    private List<Card> communityCards;
    private double pot;
    private boolean entryBetsCollected;

    Scanner scanner = new Scanner(System.in);

    public Game(){
        this.deck = new Deck();
        this.playerList = new ArrayList<>();
        this.communityCards = new ArrayList<>();
        this.pot = 0;
        this.entryBetsCollected = false;
    }

    public void setupRound(List<String> playerNames, double startingMoney, double entryBet){
        validateRoundConfiguration(playerNames, startingMoney, entryBet);
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
        this.entryBetsCollected = false;
    }

    public void startGame(){
        resetRoundState();
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
        List<Player> winners = determineWinners();
        String label = winners.size() == 1 ? "Winner: " : "Tie: ";
        System.out.println(label + winners.stream()
            .map(Player::getName)
            .reduce((first, second) -> first + ", " + second)
            .orElse("none"));
    }

    public void dealCards(){
        if (playerList.size() < 2 || playerList.size() > 9) {
            throw new IllegalStateException("A round requires between 2 and 9 players");
        }
        for (Player player : playerList) {
            if (!player.getHand().isEmpty()) {
                throw new IllegalStateException("Hole cards have already been dealt");
            }
        }
        if (deck.cardsLeft() < playerList.size() * 2) {
            throw new IllegalStateException("The deck does not contain enough hole cards");
        }
        for(Player p : playerList){
            for(int i = 0; i < 2; i++){
                p.receiveCard(deck.drawCard());
            }
        }
    }
    public void dealCommunityCards(){
        if (communityCards.size() > 0) {
            throw new IllegalStateException("Community cards have already been dealt");
        }
        for (Player player : playerList) {
            if (player.getHand().size() != 2) {
                throw new IllegalStateException("Deal all player hole cards first");
            }
        }
        if (deck.cardsLeft() < 5) {
            throw new IllegalStateException("The deck does not contain five community cards");
        }
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
        if (playerAmount < 2 || playerAmount > 9) {
            throw new IllegalArgumentException("A round requires between 2 and 9 players");
        }
        playerList.clear();
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
        validatePlayers(playerNames, startingMoney);
        if (!communityCards.isEmpty()
                || playerList.stream().anyMatch(player -> !player.getHand().isEmpty())
                || entryBetsCollected) {
            throw new IllegalStateException(
                "Cannot replace players after a round has started; use setupRound instead");
        }
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
        if (!Double.isFinite(entryBet) || entryBet < 0) {
            throw new IllegalArgumentException("Entry bet must be finite and non-negative");
        }
        if (playerList.isEmpty()) {
            throw new IllegalStateException("Register players before collecting bets");
        }
        if (entryBetsCollected) {
            throw new IllegalStateException("Entry bets have already been collected");
        }
        for (Player player : playerList) {
            if (!player.validBet(entryBet)) {
                throw new IllegalArgumentException(
                    "Entry bet exceeds the balance of player " + player.getName());
            }
        }
        for(Player p : playerList){
            p.setBet(entryBet);
            pot += entryBet;
        }
        entryBetsCollected = true;
        
    }
    public void showPot(){
        System.out.println("Pot: " + pot);
    }

    public Player determineWinner(){
        List<Player> winners = determineWinners();
        return winners.isEmpty() ? null : winners.get(0);
    }

    /** Returns every player tied for the strongest hand, in table order. */
    public List<Player> determineWinners() {
        validateCompleteRound();
        return determineWinners(playerList, communityCards);
    }

    static List<Player> determineWinners(List<Player> players, List<Card> board) {
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException("At least one player is required");
        }
        if (board == null || board.size() != 5) {
            throw new IllegalArgumentException("Exactly five community cards are required");
        }

        HandEvaluator evaluator = new HandEvaluator();
        List<Player> winners = new ArrayList<>();
        HandResult bestResult = null;
        for (Player player : players) {
            if (player == null || player.getHand().size() != 2) {
                throw new IllegalArgumentException("Every player must have exactly two hole cards");
            }
            List<Card> allCards = new ArrayList<>(player.getHand());
            allCards.addAll(board);
            HandResult result = evaluator.evaluate(allCards);
            int comparison = bestResult == null ? 1 : result.compareTo(bestResult);
            if (comparison > 0) {
                winners.clear();
                winners.add(player);
                bestResult = result;
            } else if (comparison == 0) {
                winners.add(player);
            }
        }
        return Collections.unmodifiableList(winners);
    }

    public HandResult evaluatePlayer(Player player){
        validateCompleteRound();
        if (player == null || !playerList.contains(player)) {
            throw new IllegalArgumentException("Player does not belong to this round");
        }
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

    private void validateRoundConfiguration(List<String> playerNames,
                                            double startingMoney,
                                            double entryBet) {
        validatePlayers(playerNames, startingMoney);
        if (!Double.isFinite(entryBet) || entryBet < 0 || entryBet > startingMoney) {
            throw new IllegalArgumentException(
                "Entry bet must be finite, non-negative, and no greater than starting money");
        }
    }

    private void validatePlayers(List<String> playerNames, double startingMoney) {
        if (playerNames == null || playerNames.size() < 2 || playerNames.size() > 9) {
            throw new IllegalArgumentException("A round requires between 2 and 9 players");
        }
        for (String playerName : playerNames) {
            if (playerName == null || playerName.trim().isEmpty()) {
                throw new IllegalArgumentException("Player names must not be blank");
            }
        }
        if (!Double.isFinite(startingMoney) || startingMoney < 0) {
            throw new IllegalArgumentException("Starting money must be finite and non-negative");
        }
    }

    private void validateCompleteRound() {
        if (playerList.size() < 2 || playerList.size() > 9 || communityCards.size() != 5) {
            throw new IllegalStateException("The round is not ready for showdown");
        }

        Set<Card> dealtCards = new HashSet<>(communityCards);
        if (dealtCards.size() != communityCards.size()) {
            throw new IllegalStateException("The round contains duplicate community cards");
        }
        for (Player player : playerList) {
            if (player.getHand().size() != 2) {
                throw new IllegalStateException("Every player must have exactly two hole cards");
            }
            for (Card card : player.getHand()) {
                if (!dealtCards.add(card)) {
                    throw new IllegalStateException("The round contains duplicate dealt cards");
                }
            }
        }
    }



}

    
