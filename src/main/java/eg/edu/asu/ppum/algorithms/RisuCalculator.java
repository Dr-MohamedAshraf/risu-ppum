/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.algorithms;

import eg.edu.asu.ppum.data.Itemset;
import eg.edu.asu.ppum.data.ItemsetTrie;
import eg.edu.asu.ppum.data.Transaction;
import eg.edu.asu.ppum.data.TransactionDatabase;
import eg.edu.asu.ppum.data.UtilityTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Line 1 of Algorithms 1-3: a single scan of the database that finds the sensitive transactions, those that
 * contain at least one sensitive itemset, and computes the Real Item Sensitive Utility of every sensitive item
 * (Definition 14, Eq. 2): RISU(x) is the sum of u(x, T) over the sensitive transactions T that contain x. The
 * scan also records which sensitive itemsets each sensitive transaction contains, for the later steps.
 */
final class RisuCalculator {

    private final List<Integer> sensitiveTransactions;
    private final Map<Integer, int[]> itemsetsByTransaction;
    private final Map<Integer, Long> risu;

    /**
     * Stores the results of a scan.
     */
    private RisuCalculator(List<Integer> sensitiveTransactions, Map<Integer, int[]> itemsetsByTransaction,
                           Map<Integer, Long> risu) {
        this.sensitiveTransactions = Collections.unmodifiableList(sensitiveTransactions);
        this.itemsetsByTransaction = itemsetsByTransaction;
        this.risu = Collections.unmodifiableMap(risu);
    }

    /**
     * Scans the database once and returns the sensitive transactions and the RISU value of every sensitive item.
     */
    static RisuCalculator scan(TransactionDatabase database, HidingProblem problem) {
        UtilityTable profits = problem.profits();
        List<Itemset> itemsets = problem.sensitiveItemsets();
        ItemsetTrie trie = new ItemsetTrie(itemsets);
        Map<Integer, Long> risu = new HashMap<Integer, Long>();
        for (Itemset itemset : itemsets) {
            for (int i = 0; i < itemset.size(); i++) {
                risu.put(itemset.get(i), 0L);
            }
        }
        List<Integer> sensitive = new ArrayList<Integer>();
        Map<Integer, int[]> itemsetsByTransaction = new HashMap<Integer, int[]>();
        final List<Integer> found = new ArrayList<Integer>();
        for (int tid = 0; tid < database.size(); tid++) {
            Transaction transaction = database.get(tid);
            found.clear();
            trie.forEachContained(transaction, profits, (index, utility) -> found.add(index));
            if (found.isEmpty()) {
                continue;
            }
            sensitive.add(tid);
            itemsetsByTransaction.put(tid, sortedArray(found));
            for (int position = 0; position < transaction.size(); position++) {
                int item = transaction.itemAt(position);
                Long sum = risu.get(item);
                if (sum != null) {
                    risu.put(item, sum + (long) transaction.quantityAt(position) * profits.unitProfit(item));
                }
            }
        }
        return new RisuCalculator(sensitive, itemsetsByTransaction, risu);
    }

    /**
     * Returns the TIDs of the sensitive transactions, in increasing order.
     */
    List<Integer> sensitiveTransactions() {
        return sensitiveTransactions;
    }

    /**
     * Returns the indices of the sensitive itemsets that a sensitive transaction contains, in increasing order.
     */
    int[] itemsetsIn(int tid) {
        return itemsetsByTransaction.get(tid);
    }

    /**
     * Returns RISU(x) for every item x that belongs to at least one sensitive itemset.
     */
    Map<Integer, Long> risu() {
        return risu;
    }

    /**
     * Copies a list of indices into an array sorted in increasing order.
     */
    private static int[] sortedArray(List<Integer> values) {
        int[] array = new int[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        java.util.Arrays.sort(array);
        return array;
    }
}
