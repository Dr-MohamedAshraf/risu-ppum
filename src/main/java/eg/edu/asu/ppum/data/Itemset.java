/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.data;

import java.util.Arrays;

/**
 * An itemset together with its utility in the original database, such as a high-utility itemset read from the
 * output of a miner. The items keep the order in which they were written, which is used to break ties when a
 * victim item is chosen. Two itemsets are equal when they contain the same items, whatever their order, and
 * they are ordered by their items in increasing order, compared one by one.
 */
public final class Itemset implements Comparable<Itemset> {

    private final int[] items;
    private final int[] sortedItems;
    private final long utility;

    /**
     * Creates an itemset from its items, in the order in which they were written, and its utility. There must be
     * at least one item, and no item may appear twice.
     */
    public Itemset(int[] items, long utility) {
        if (items.length == 0) {
            throw new IllegalArgumentException("an itemset needs at least one item");
        }
        this.items = items.clone();
        this.sortedItems = items.clone();
        Arrays.sort(this.sortedItems);
        for (int i = 1; i < sortedItems.length; i++) {
            if (sortedItems[i] == sortedItems[i - 1]) {
                throw new IllegalArgumentException("item " + sortedItems[i] + " appears twice in an itemset");
            }
        }
        this.utility = utility;
    }

    /**
     * Returns the number of items.
     */
    public int size() {
        return items.length;
    }

    /**
     * Returns the item at an index, from 0 to {@code size() - 1}, in the order in which the items were written.
     */
    public int get(int index) {
        return items[index];
    }

    /**
     * Tells whether the itemset contains an item.
     */
    public boolean contains(int item) {
        return Arrays.binarySearch(sortedItems, item) >= 0;
    }

    /**
     * Returns the utility of the itemset in the original database, u(X) (Definition 3 of the paper).
     */
    public long utility() {
        return utility;
    }

    /**
     * Returns the items in increasing order, without copying; the caller must not change the array.
     */
    int[] sortedItems() {
        return sortedItems;
    }

    /**
     * Two itemsets are equal when they contain the same items, in any order; utilities are not compared.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof Itemset && Arrays.equals(sortedItems, ((Itemset) other).sortedItems);
    }

    /**
     * Returns a hash code consistent with {@link #equals}.
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(sortedItems);
    }

    /**
     * Compares two itemsets by their items in increasing order, one by one; when one itemset starts the other,
     * the shorter comes first. This order does not depend on how a miner wrote or listed the itemsets.
     */
    @Override
    public int compareTo(Itemset other) {
        int common = Math.min(sortedItems.length, other.sortedItems.length);
        for (int i = 0; i < common; i++) {
            if (sortedItems[i] != other.sortedItems[i]) {
                return Integer.compare(sortedItems[i], other.sortedItems[i]);
            }
        }
        return Integer.compare(sortedItems.length, other.sortedItems.length);
    }

    /**
     * Writes the itemset in SPMF format: the items separated by spaces, then "#UTIL:" and the utility.
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (int item : items) {
            text.append(item).append(' ');
        }
        return text.append("#UTIL: ").append(utility).toString();
    }
}
