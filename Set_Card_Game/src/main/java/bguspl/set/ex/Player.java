package bguspl.set.ex;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    final int EMPTY = -1;
    final int SECOND_IN_MILLIS = 1000;
    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private Dealer dealer;

    public int activeTokens;

    private BlockingQueue<Integer> keyspressed;

    public int[] set;
    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */

    public boolean shouldPoint;
    public boolean shouldPenalty;

    public Object lock;

    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        this.activeTokens = 0;
        keyspressed = new LinkedBlockingQueue<>(env.config.featureSize);
        set = new int[env.config.featureSize];
        for (int i = 0; i < set.length; i++)
            set[i] = EMPTY;
        terminate = false;
        shouldPoint = false;
        shouldPenalty = false;
        lock = new Object();
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human)
            createArtificialIntelligence();
        else {
            while (!terminate) {
                // TODO implement main player loop
                synchronized (this) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                    }
                }
                play();
            }
        }
        if (!human)
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            Random rnd = new Random();
            while (!terminate) {
                // TODO implement player key press simulator
                if (dealer.cardsPlaced) {
                    int pressSlot = rnd.nextInt(env.config.tableSize);
                    this.keyspressed.add(pressSlot);
                    play();
                }
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        if (!human)
            aiThread.interrupt();
    }

    private void play() {
        tokenHandling();
        if (shouldPoint) {
            point();
            shouldPoint = false;
    } else if (shouldPenalty) {
            penalty();
            shouldPenalty = false;
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        if (dealer.cardsPlaced && !shouldPenalty && !shouldPoint) {
            try {
                keyspressed.put(slot);
            } catch (InterruptedException e) {
            }
        }
        synchronized (this) {
            this.notify();
        }
    }

    public void tokenHandling() {
        synchronized (keyspressed) {
            if (!keyspressed.isEmpty()) {
                int slot = keyspressed.remove();
                if (table.slotToCard[slot] != null) {
                    if (table.isTokenPlaced(id, slot)) {
                        removeToken(slot);
                    } else if (activeTokens < env.config.featureSize) {
                        addToken(slot);
                        if (activeTokens == env.config.featureSize) {
                            synchronized (dealer) {
                                dealer.notify();
                                synchronized (dealer.waitingPlayers) {
                                    dealer.addWaiting(id);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void removeToken(int slot) {
        synchronized (table.slots[slot]) {
            boolean isRemoved = false;
            for (int i = 0; i < set.length; i++)
                if (set[i] == slot) {
                    set[i] = EMPTY;
                    isRemoved = true;
                    break;
                }
            if (isRemoved) {
                activeTokens--;
                table.removeToken(id, slot);
            }
        }
    }

    public void addToken(int slot) {
        synchronized (table.slots[slot]) {
            if (table.slotToCard[slot] != null) {
                for (int i = 0; i < set.length; i++)
                    if (set[i] == EMPTY) {
                        set[i] = slot;
                        break;
                    }
                activeTokens++;
                table.placeToken(id, slot);
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        env.ui.setFreeze(id, env.config.pointFreezeMillis);
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);

        try {
            Thread.sleep(env.config.pointFreezeMillis);
        } catch (InterruptedException e) {
        }

        env.ui.setFreeze(id, 0);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        for (long i = env.config.penaltyFreezeMillis; i > 0; i -= SECOND_IN_MILLIS) {

            env.ui.setFreeze(id, i);
            try {
                Thread.sleep(SECOND_IN_MILLIS);
            } catch (InterruptedException e) {
            }
        }
        env.ui.setFreeze(id, 0);
    }

    public int score() {
        return score;
    }
}