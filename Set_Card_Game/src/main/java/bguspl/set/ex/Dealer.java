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

    public volatile Queue<Integer> waitingPlayers;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    private Thread[] playerThreads;
    public boolean cardsPlaced;

    // public Semaphore dealerSemaphore;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        waitingPlayers = new LinkedList<Integer>();
        playerThreads = new Thread[env.config.players];
        cardsPlaced = false;
        // dealerSemaphore = new Semaphore(1, true);
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        for (int i = 0; i < players.length; i++) {
            playerThreads[i] = new Thread(players[i]);
            playerThreads[i].start();
        }
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        removeAllCardsFromTable();
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            // sleepUntilWokenOrTimeout();
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
        for (int i = players.length - 1; i >= 0; i--)
            players[i].terminate();
        for (int i = playerThreads.length - 1; i >= 0; i--)
            playerThreads[i].interrupt();

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
        int awardplayer = -1;
        synchronized (waitingPlayers) {
            if (!waitingPlayers.isEmpty()) {
                awardplayer = waitingPlayers.remove();
            }
        }
        if (awardplayer != -1) {
            env.logger.info("working on player " + (awardplayer + 1));
            if (isSetStillValid(awardplayer)) {
                System.out.println(
                        "the set of player " + (awardplayer + 1) + " now: " + players[awardplayer].set[0] + ", "
                                + players[awardplayer].set[1] + ", "
                                + players[awardplayer].set[2]);
                boolean isSet = env.util.testSet(convertToCards(players[awardplayer].set));
                if (isSet) {
                    for (int i = 0; i < players[awardplayer].set.length; i++) {
                        int slotId = players[awardplayer].set[i];
                        for (int j = 0; j < players.length; j++) {
                            players[j].removeToken(slotId);
                        }
                        table.removeCard(slotId);
                    }
                    reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
                    updateTimerDisplay(true);
                    players[awardplayer].shouldPoint = true;

                } else {
                    players[awardplayer].shouldPenalty = true;
                }
            }
            synchronized (players[awardplayer]) {
                players[awardplayer].notifyAll();
            }
            /*
             * synchronized (players[awardplayer].lock) {
             * players[awardplayer].lock.notifyAll();
             * // System.out.println("Player: " + awardplayer + " woke up");
             * }
             */
        }
    }

    /*
     * if (!waitingPlayers.isEmpty()) {
     * int awardplayer = waitingPlayers.remove();
     * if (isSetStillValid(awardplayer)) {
     * boolean isSet = env.util.testSet(convertToCards(players[awardplayer].set));
     * if (isSet) {
     * for (int i = 0; i < players[awardplayer].set.length; i++) {
     * synchronized (table.slots[i]) {
     * table.removeCard(players[awardplayer].set[i]);
     * for (int j = 0; j < players.length; j++) {
     * if (players[j].set[i] != -1)
     * table.removeToken(j, players[j].set[i]);
     * }
     * table.slotToCard[players[awardplayer].set[i]] = null;
     * table.cardToSlot[players[awardplayer].set[i]] = null;
     * players[awardplayer].set[i] = -1;
     * }
     * }
     * players[awardplayer].activeTokens = 0;
     * reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
     * updateTimerDisplay(true);
     * players[awardplayer].shouldPoint = true;
     * } else {
     * players[awardplayer].shouldPenalty = true;
     * }
     * players[awardplayer].isFrozen = false;
     * }
     * }
     */

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        cardsPlaced = false;
        Collections.shuffle(deck);
        List<Integer> empties = table.getAllEmptySlots();
        Random rand = new Random();
        while (!deck.isEmpty() && empties.size() > 0) {
            int randomIndex = rand.nextInt(empties.size());
            table.placeCard(deck.remove(0), empties.remove(randomIndex));
        }
        cardsPlaced = true;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private synchronized void sleepUntilWokenOrTimeout() {
        // TODO implement
        try {
            /*
             * if (reshuffleTime - System.currentTimeMillis() <
             * env.config.turnTimeoutWarningMillis)
             * this.wait(10);
             * else
             */
            this.wait(100);
        } catch (InterruptedException e) {
        }
        /*
         * try {
         * Thread.sleep(100);
         * } catch (InterruptedException e) {
         * }
         */
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
                synchronized (players[i]) {
                    for (int j = 0; j < table.tokenToSlot[i].length; j++) {
                        players[i].removeToken(j);
                    }
                }
            }
            for (int i = 0; i < table.slotToCard.length; i++) {
                if (table.slotToCard[i] != null) {
                    deck.add(table.slotToCard[i]);
                    table.removeCard(i);
                }
            }
            for (int i = 0; i < players.length; i++) {
                synchronized (players[i]) {
                    players[i].notifyAll();
                }
            }
            waitingPlayers = new LinkedList<>();
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
        this.terminate();
    }

    private int[] findWinners() {
        int max = 0;
        for (Player p : players) {
            if (p.score() > max)
                max = p.score();
        }
        int winnerCount = 0;
        for (Player p : players) {
            if (p.score() == max)
                winnerCount++;
        }
        int[] winners = new int[winnerCount];
        winnerCount = 0;
        for (Player p : players) {
            if (p.score() == max) {
                winners[winnerCount] = p.id;
                winnerCount++;
            }
        }
        return winners;
    }

    /*
     * public void addToWaiting(int id) {
     * synchronized (waitingPlayers) {
     * waitingPlayers.add(id);
     * players[id].isFrozen = true;
     * }
     * }
     */

    private boolean isSetStillValid(int id) {
        for (int i = 0; i < players[id].set.length; i++) {
            if (players[id].set[i] == -1)
                return false;
            else if (table.slotToCard[players[id].set[i]] == null)
                return false;
        }
        return true;
    }

    private int[] convertToCards(int[] setSlots) {
        int[] cards = new int[setSlots.length];
        for (int i = 0; i < cards.length; i++) {
            cards[i] = table.slotToCard[setSlots[i]];
        }
        return cards;
    }

    public void addWaiting(int id) {
        try {
            Thread.sleep(10);
            // dealerSemaphore.acquire();
        } catch (InterruptedException e) {
        }
        waitingPlayers.add(id);
        // dealerSemaphore.release();
    }
}