package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    protected final Boolean[][] tokenToSlot;

    public final Object[] slots;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if
     *                   none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if
     *                   none).
     */

    private Semaphore tableSemaphore;

    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        tokenToSlot = new Boolean[env.config.players][env.config.tableSize];
        for (int i = 0; i < tokenToSlot.length; i++) {
            for (int j = 0; j < tokenToSlot[i].length; j++) {
                tokenToSlot[i][j] = false;
            }
        }
        this.slots = new Object[env.config.tableSize];
        for (int i = 0; i < slots.length; i++)
            slots[i] = new Object();
        tableSemaphore = new Semaphore(1, true);
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the
     * table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted()
                    .collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(
                    sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * 
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        synchronized (slots[slot]) {
            try {
                tableSemaphore.acquire();
            } catch (InterruptedException e) {
            }
            try {
                Thread.sleep(env.config.tableDelayMillis);
            } catch (InterruptedException ignored) {
            }

            cardToSlot[card] = slot;
            slotToCard[slot] = card;

            // TODO implement

            env.ui.placeCard(card, slot);
            tableSemaphore.release();
        }
    }

    /**
     * Removes a card from a grid slot on the table.
     * 
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        synchronized (slots[slot]) {
            try {
                tableSemaphore.acquire();
            } catch (InterruptedException e) {
            }
            try {
                Thread.sleep(env.config.tableDelayMillis);
            } catch (InterruptedException ignored) {
            }

            // TODO implement
            cardToSlot[slotToCard[slot]] = null;
            slotToCard[slot] = null;
            env.ui.removeCard(slot);
            tableSemaphore.release();
        }
    }

    /**
     * Places a player token on a grid slot.
     * 
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        // TODO implement
        try {
            tableSemaphore.acquire();
        } catch (InterruptedException e) {
        }
        env.ui.placeToken(player, slot);
        tokenToSlot[player][slot] = true;
        tableSemaphore.release();
    }

    /**
     * Removes a token of a player from a grid slot.
     * 
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        // TODO implement
        try {
            tableSemaphore.acquire();
        } catch (InterruptedException e) {
        }
        env.ui.removeToken(player, slot);
        tokenToSlot[player][slot] = false;
        tableSemaphore.release();
        return false;
    }

    /*
     * Returns all the empty slots.
     **/
    public List<Integer> getAllEmptySlots() {
        List<Integer> output = new LinkedList<Integer>();
        for (int i = 0; i < env.config.tableSize; i++) {
            if (slotToCard[i] == null)
                output.add(i);
        }
        return output;
    }

    public boolean isTokenPlaced(int player, int slot) {
        synchronized (slots[slot]) {
            return tokenToSlot[player][slot];
        }
    }
}