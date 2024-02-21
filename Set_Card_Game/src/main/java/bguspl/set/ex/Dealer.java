package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    private Queue<Integer> waitingPlayers;

    private Semaphore dealerSemaphore;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        waitingPlayers = new LinkedList<Integer>();
        dealerSemaphore = new Semaphore(1, true);
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        for (int i = 0; i < players.length; i++) {
            Thread player = new Thread(players[i]);
            player.start();
        }
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        for(Player p : players)
            p.terminate();
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement

        if (!waitingPlayers.isEmpty()) {
            int awardplayer = waitingPlayers.remove();
            if (isSetStillValid(awardplayer)) {

                synchronized (players[awardplayer]) {
                    boolean isSet = env.util.testSet(convertToCards(players[awardplayer].set));
                    if (isSet) {
                        for (int i = 0; i < players[awardplayer].set.length; i++) {
                            synchronized (table.slots[i]) {
                                table.removeCard(players[awardplayer].set[i]);
                                for (int j = 0; j < players.length; j++) {
                                    if (players[j].set[i] != -1)
                                        table.removeToken(awardplayer, players[j].set[i]);
                                }
                                table.slotToCard[players[awardplayer].set[i]] = null;
                                table.cardToSlot[players[awardplayer].set[i]] = null;
                                players[awardplayer].set[i] = -1;
                            }
                        }
                        players[awardplayer].activeTokens = 0;
                        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
                        updateTimerDisplay(true);
                        players[awardplayer].shouldPoint = true;
                    } else {
                        players[awardplayer].shouldPenalty = true;
                    }
                }
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        Collections.shuffle(deck);
        while (!deck.isEmpty() && table.countCards() < 12) {
            List<Integer> empties = table.getAllEmptySlots();
            Random rand = new Random();
            int randomIndex = rand.nextInt(empties.size());
            table.placeCard(deck.get(0), empties.get(randomIndex));
            deck.remove(0);
            empties.remove(randomIndex);
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private synchronized void sleepUntilWokenOrTimeout() {
        // TODO implement
        try {
            this.wait(500);
        } catch (InterruptedException e) {
        }
        this.notifyAll();

    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        boolean color = false;
        if (reset) {
            env.ui.setCountdown(env.config.turnTimeoutMillis, color);
        } else {
            if (reshuffleTime - System.currentTimeMillis() < env.config.turnTimeoutWarningMillis)
                color = true;
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), color);
        }

    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        synchronized (table) {

            for (int i = 0; i < table.tokenToSlot.length; i++) {
                for (int j = 0; j < table.tokenToSlot[i].length; j++) {
                    table.tokenToSlot[i][j] = false;
                    table.removeToken(i, j);
                }
            }
            for (int i = 0; i < players.length; i++) {
                synchronized (players[i]) {
                    players[i].activeTokens = 0;
                }
            }
            for (int i = 0; i < table.slotToCard.length; i++) {
                deck.add(table.slotToCard[i]);
                table.slotToCard[i] = null;
                table.removeCard(i);
            }
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        int[] winners = findWinners();
        env.ui.announceWinner(winners);
    }

    private int[] findWinners() {
        int max = 0;
        for(Player p : players){
            if(p.score() > max)
                max = p.score();            
        }
        int winnerCount = 0;
        for(Player p : players){
            if(p.score() == max)
                winnerCount++;
        }
        int[] winners = new int[winnerCount];
        winnerCount = 0;
        for(Player p : players){
            if(p.score() == max){
                winners[winnerCount] = p.id;
                winnerCount++;
            }
        }
        return winners;
    }

    public void addToWaiting(int id) {
        synchronized (waitingPlayers) {
            waitingPlayers.add(id);
        }
    }

    private boolean isSetStillValid(int id) {
        for (int i = 0; i < players[id].set.length; i++) {
            if (!table.isTokenPlaced(id, players[id].set[i]))
                return false;
        }
        return true;
    }

    private int[] convertToCards(int[] cardIds) {
        int[] cards = new int[cardIds.length];
        for (int i = 0; i < cards.length; i++)
            cards[i] = table.slotToCard[cardIds[i]];
        return cards;
    }
}