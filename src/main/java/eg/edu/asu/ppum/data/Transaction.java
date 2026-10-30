/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.data;

import java.util.Arrays;

/**
 * One transaction of the database: the items it contains and the quantity (internal utility) of each one, in
 * the order in which they appear in the input line. A sanitization algorithm changes a transaction only by
 * setting a quantity or removing an item, through {@link #setQuantity} and {@link #remove}.
 *
 * <p>Items are read by position, from 0 to {@code size() - 1}. Removing an item moves the items after it one
 * position to the left, so do not remove items while looping over positions in increasing order.</p>
 */
public final class Transaction {

    private int[] items;
    private int[] quantities;
    private int size;

    /**
     * Creates a transaction from parallel arrays of items and quantities, which are used as given.
     */
    Transaction(int[] items, int[] quantities) {
        this.items = items;
        this.quantities = quantities;
        this.size = items.length;
    }

    /**
     * Returns the number of items in the transaction.
     */
    public int size() {
        return size;
    }

    /**
     * Tells whether the transaction has no items, for example after all of them were removed.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the item at a position, in the order of the input line.
     */
    public int itemAt(int position) {
        checkPosition(position);
        return items[position];
    }

    /**
     * Returns the quantity of the item at a position.
     */
    public int quantityAt(int position) {
        checkPosition(position);
        return quantities[position];
    }

    /**
     * Returns the quantity of an item, or 0 if the transaction does not contain it.
     */
    public int quantityOf(int item) {
        int position = positionOf(item);
        return position < 0 ? 0 : quantities[position];
    }

    /**
     * Tells whether the transaction contains an item.
     */
    public boolean contains(int item) {
        return positionOf(item) >= 0;
    }

    /**
     * Tells whether the transaction contains every item of an itemset, that is, whether it supports the
     * itemset.
     */
    public boolean containsAll(Itemset itemset) {
        for (int i = 0; i < itemset.size(); i++) {
            if (positionOf(itemset.get(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sets the quantity of an item. A quantity of 0 removes the item, and an item that the transaction does not
     * contain yet is added at the end.
     */
    public void setQuantity(int item, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("a quantity cannot be negative: " + quantity);
        }
        int position = positionOf(item);
        if (quantity == 0) {
            if (position >= 0) {
                removeAt(position);
            }
        } else if (position >= 0) {
            quantities[position] = quantity;
        } else {
            append(item, quantity);
        }
    }

    /**
     * Removes an item from the transaction. Nothing happens if the transaction does not contain it.
     */
    public void remove(int item) {
        int position = positionOf(item);
        if (position >= 0) {
            removeAt(position);
        }
    }

    /**
     * Returns u(x, T), the utility of an item in this transaction: its quantity times its unit profit
     * (Definition 1 of the paper), or 0 if the transaction does not contain the item.
     */
    public long utilityOf(int item, UtilityTable profits) {
        int position = positionOf(item);
        return position < 0 ? 0 : (long) quantities[position] * profits.unitProfit(item);
    }

    /**
     * Returns u(X, T), the utility of an itemset in this transaction: the sum of the utilities of its items when
     * the transaction contains all of them, otherwise 0 (Definition 2 of the paper).
     */
    public long utilityOf(Itemset itemset, UtilityTable profits) {
        long utility = 0;
        for (int i = 0; i < itemset.size(); i++) {
            int item = itemset.get(i);
            int position = positionOf(item);
            if (position < 0) {
                return 0;
            }
            utility += (long) quantities[position] * profits.unitProfit(item);
        }
        return utility;
    }

    /**
     * Returns TU(T), the transaction utility: the sum of the utilities of all its items (Definition 4 of the
     * paper).
     */
    public long transactionUtility(UtilityTable profits) {
        long utility = 0;
        for (int i = 0; i < size; i++) {
            utility += (long) quantities[i] * profits.unitProfit(items[i]);
        }
        return utility;
    }

    /**
     * Tells whether another transaction holds exactly the same items with the same quantities, in any order.
     */
    public boolean hasSameContentAs(Transaction other) {
        if (other.size != size) {
            return false;
        }
        int same = 0;
        while (same < size && items[same] == other.items[same] && quantities[same] == other.quantities[same]) {
            same++;
        }
        for (int i = same; i < size; i++) {
            if (other.quantityOf(items[i]) != quantities[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tells whether every item of this transaction also appears in another transaction with at least the same
     * quantity, that is, whether this transaction could have been obtained from the other one only by removing
     * items and lowering quantities.
     */
    public boolean isReductionOf(Transaction other) {
        for (int i = 0; i < size; i++) {
            if (other.quantityOf(items[i]) < quantities[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns an independent copy of the transaction.
     */
    public Transaction copy() {
        return new Transaction(Arrays.copyOf(items, size), Arrays.copyOf(quantities, size));
    }

    /**
     * Writes the transaction as in the input files: "item:quantity" pairs separated by single spaces.
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                text.append(' ');
            }
            text.append(items[i]).append(':').append(quantities[i]);
        }
        return text.toString();
    }

    /**
     * Returns the position of an item, or -1 if the transaction does not contain it. Transactions are short, so
     * a linear search is fast and keeps the memory needed per transaction small.
     */
    private int positionOf(int item) {
        for (int i = 0; i < size; i++) {
            if (items[i] == item) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Removes the item at a position and moves the items after it one position to the left.
     */
    private void removeAt(int position) {
        int moved = size - position - 1;
        System.arraycopy(items, position + 1, items, position, moved);
        System.arraycopy(quantities, position + 1, quantities, position, moved);
        size--;
    }

    /**
     * Adds an item at the end, enlarging the arrays when they are full.
     */
    private void append(int item, int quantity) {
        if (size == items.length) {
            int capacity = Math.max(4, size * 2);
            items = Arrays.copyOf(items, capacity);
            quantities = Arrays.copyOf(quantities, capacity);
        }
        items[size] = item;
        quantities[size] = quantity;
        size++;
    }

    /**
     * Checks that a position lies between 0 and {@code size() - 1}.
     */
    private void checkPosition(int position) {
        if (position < 0 || position >= size) {
            throw new IndexOutOfBoundsException("position " + position + " in a transaction of " + size + " items");
        }
    }
}
