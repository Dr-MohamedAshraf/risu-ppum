/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A prefix tree (trie) of itemsets that finds, for one transaction, every stored itemset the transaction
 * contains and the itemset's utility in it, without testing the itemsets one by one. Itemsets that start with
 * the same items share a path in the tree, and a transaction only walks down the paths of items it contains.
 * This keeps two costly steps fast even with many itemsets: counting the sensitive and non-sensitive itemsets
 * of each transaction (Weighted Sorting) and recomputing the utility of every high-utility itemset after
 * sanitization.
 *
 * <p>A tree reuses internal buffers, so one tree must not be used by several threads at the same time.</p>
 */
public final class ItemsetTrie {

    /**
     * Receives each stored itemset that a transaction contains.
     */
    public interface Visitor {

        /**
         * Called once for every stored itemset contained in the transaction, with the index of the itemset in
         * the list the tree was built from and its utility in the transaction, u(X, T).
         */
        void found(int itemsetIndex, long utilityInTransaction);
    }

    private static final int[] NO_RANKS = new int[0];
    private static final Node[] NO_NODES = new Node[0];
    private static final int LARGEST_ITEM_FOR_ARRAY_LOOKUP = 1 << 22;

    private final int itemsetCount;
    private final Node root = new Node(-1);
    private final int[] rankByItem;
    private final Map<Integer, Integer> rankByLargeItem;
    private final int[] positionByRank;

    private long[] packed = new long[16];
    private int[] ranks = new int[16];
    private long[] utilities = new long[16];
    private int length;
    private Visitor visitor;

    /**
     * Builds the tree from a list of distinct itemsets. Inside the tree an item is represented by its rank, its
     * position among all the items of the itemsets sorted in increasing order.
     */
    public ItemsetTrie(List<Itemset> itemsets) {
        this.itemsetCount = itemsets.size();
        int[] items = distinctItems(itemsets);
        int largest = items.length == 0 ? -1 : items[items.length - 1];
        if (largest < LARGEST_ITEM_FOR_ARRAY_LOOKUP) {
            rankByItem = new int[largest + 1];
            Arrays.fill(rankByItem, -1);
            for (int rank = 0; rank < items.length; rank++) {
                rankByItem[items[rank]] = rank;
            }
            rankByLargeItem = null;
        } else {
            rankByItem = null;
            rankByLargeItem = new HashMap<Integer, Integer>();
            for (int rank = 0; rank < items.length; rank++) {
                rankByLargeItem.put(items[rank], rank);
            }
        }
        positionByRank = new int[items.length];
        Arrays.fill(positionByRank, -1);
        insertAll(itemsets);
        freeze(root);
    }

    /**
     * Calls the visitor for every stored itemset that the transaction contains.
     */
    public void forEachContained(Transaction transaction, UtilityTable profits, Visitor receiver) {
        if (root.children.length == 0) {
            return;
        }
        load(transaction, profits);
        try {
            visitor = receiver;
            walk(root, 0, 0L);
        } finally {
            for (int k = 0; k < length; k++) {
                positionByRank[ranks[k]] = -1;
            }
            visitor = null;
        }
    }

    /**
     * Returns the utility of every stored itemset in a set of transactions, in the order of the list the tree
     * was built from: the sum of u(X, T) over the transactions T that contain X.
     */
    public long[] utilities(Iterable<Transaction> transactions, UtilityTable profits) {
        final long[] totals = new long[itemsetCount];
        for (Transaction transaction : transactions) {
            forEachContained(transaction, profits, (index, utility) -> totals[index] += utility);
        }
        return totals;
    }

    /**
     * Returns the rank of an item, or -1 if no stored itemset contains it.
     */
    private int rankOf(int item) {
        if (rankByItem != null) {
            return item < rankByItem.length ? rankByItem[item] : -1;
        }
        Integer rank = rankByLargeItem.get(item);
        return rank == null ? -1 : rank;
    }

    /**
     * Copies the items of the transaction that appear in the tree into the buffers, sorted by rank, together
     * with their utilities in the transaction, and records where each rank is.
     */
    private void load(Transaction transaction, UtilityTable profits) {
        int size = transaction.size();
        if (packed.length < size) {
            packed = new long[size];
            ranks = new int[size];
            utilities = new long[size];
        }
        length = 0;
        for (int position = 0; position < size; position++) {
            int rank = rankOf(transaction.itemAt(position));
            if (rank >= 0) {
                packed[length++] = ((long) rank << 32) | position;
            }
        }
        Arrays.sort(packed, 0, length);
        for (int k = 0; k < length; k++) {
            int rank = (int) (packed[k] >>> 32);
            int position = (int) packed[k];
            ranks[k] = rank;
            utilities[k] = (long) transaction.quantityAt(position) * profits.unitProfit(transaction.itemAt(position));
            positionByRank[rank] = k;
        }
    }

    /**
     * Visits the children of a node whose items appear in the loaded transaction after position {@code from}.
     * It loops over the children or over the remaining items of the transaction, whichever is shorter.
     */
    private void walk(Node node, int from, long utility) {
        int[] childRanks = node.childRanks;
        if (childRanks.length <= length - from) {
            for (int c = 0; c < childRanks.length; c++) {
                int k = positionByRank[childRanks[c]];
                if (k >= from) {
                    enter(node.children[c], k, utility + utilities[k]);
                }
            }
        } else {
            for (int k = from; k < length; k++) {
                int c = Arrays.binarySearch(childRanks, ranks[k]);
                if (c >= 0) {
                    enter(node.children[c], k, utility + utilities[k]);
                }
            }
        }
    }

    /**
     * Reports the itemset that ends at a node, if any, and continues with the node's children.
     */
    private void enter(Node node, int position, long utility) {
        if (node.itemset >= 0) {
            visitor.found(node.itemset, utility);
        }
        if (node.children.length > 0) {
            walk(node, position + 1, utility);
        }
    }

    /**
     * Inserts the itemsets in lexicographic order of their ranks. In that order each itemset shares a prefix
     * with the previous one, so new nodes are always appended as last children and stay sorted.
     */
    private void insertAll(List<Itemset> itemsets) {
        final int[][] paths = new int[itemsets.size()][];
        int longest = 0;
        for (int i = 0; i < paths.length; i++) {
            int[] sorted = itemsets.get(i).sortedItems();
            paths[i] = new int[sorted.length];
            for (int k = 0; k < sorted.length; k++) {
                paths[i][k] = rankOf(sorted[k]);
            }
            longest = Math.max(longest, sorted.length);
        }
        Integer[] order = new Integer[paths.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> compare(paths[a], paths[b]));
        Node[] nodesOnPath = new Node[longest + 1];
        nodesOnPath[0] = root;
        int[] previous = NO_RANKS;
        for (int index : order) {
            int[] path = paths[index];
            int shared = sharedPrefixLength(previous, path);
            if (shared == path.length && shared == previous.length) {
                throw new IllegalArgumentException("itemset " + itemsets.get(index) + " is listed twice");
            }
            Node node = nodesOnPath[shared];
            for (int k = shared; k < path.length; k++) {
                Node child = new Node(path[k]);
                node.add(child);
                nodesOnPath[k + 1] = child;
                node = child;
            }
            node.itemset = index;
            previous = path;
        }
    }

    /**
     * Returns the distinct items of all itemsets, in increasing order.
     */
    private static int[] distinctItems(List<Itemset> itemsets) {
        Set<Integer> items = new HashSet<Integer>();
        for (Itemset itemset : itemsets) {
            for (int item : itemset.sortedItems()) {
                items.add(item);
            }
        }
        int[] sorted = new int[items.size()];
        int i = 0;
        for (int item : items) {
            sorted[i++] = item;
        }
        Arrays.sort(sorted);
        return sorted;
    }

    /**
     * Compares two rank sequences lexicographically; a sequence comes before any longer sequence it starts.
     */
    private static int compare(int[] a, int[] b) {
        int common = Math.min(a.length, b.length);
        for (int k = 0; k < common; k++) {
            if (a[k] != b[k]) {
                return Integer.compare(a[k], b[k]);
            }
        }
        return Integer.compare(a.length, b.length);
    }

    /**
     * Returns how many leading ranks two sequences have in common.
     */
    private static int sharedPrefixLength(int[] a, int[] b) {
        int common = Math.min(a.length, b.length);
        int k = 0;
        while (k < common && a[k] == b[k]) {
            k++;
        }
        return k;
    }

    /**
     * Turns the child lists built during insertion into compact sorted arrays.
     */
    private static void freeze(Node node) {
        if (node.pending == null) {
            return;
        }
        node.children = node.pending.toArray(NO_NODES);
        node.childRanks = new int[node.children.length];
        for (int c = 0; c < node.children.length; c++) {
            node.childRanks[c] = node.children[c].rank;
            freeze(node.children[c]);
        }
        node.pending = null;
    }

    /**
     * A node of the tree: the rank of its item, the index of the itemset that ends here (or -1), and its
     * children sorted by rank.
     */
    private static final class Node {

        private final int rank;
        private int itemset = -1;
        private int[] childRanks = NO_RANKS;
        private Node[] children = NO_NODES;
        private List<Node> pending;

        /**
         * Creates a node for the item with the given rank.
         */
        Node(int rank) {
            this.rank = rank;
        }

        /**
         * Appends a child while the tree is being built.
         */
        void add(Node child) {
            if (pending == null) {
                pending = new ArrayList<Node>(2);
            }
            pending.add(child);
        }
    }
}
