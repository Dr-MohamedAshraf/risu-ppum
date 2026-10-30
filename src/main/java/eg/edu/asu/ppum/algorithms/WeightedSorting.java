/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.algorithms;

import eg.edu.asu.ppum.data.ItemsetTrie;
import eg.edu.asu.ppum.data.TransactionDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Weighted Sorting, Lines 5-8 of Algorithms 1-3 (Definitions 9-13, Eq. 1). Every sensitive transaction T gets
 * the weight Wt(T) = SC(T) / (NSC(T) + 1), where SC(T) is the number of sensitive itemsets and NSC(T) the
 * number of non-sensitive high-utility itemsets that T contains. The transactions are then visited in
 * decreasing order of weight, so those that support many sensitive itemsets and few non-sensitive ones are
 * modified first. Transactions with the same weight keep their database order.
 */
final class WeightedSorting {

    private WeightedSorting() {
    }

    /**
     * Returns the TIDs of the sensitive transactions in decreasing order of weight.
     */
    static List<Integer> sort(TransactionDatabase database, RisuCalculator scan, HidingProblem problem) {
        ItemsetTrie nonSensitive = new ItemsetTrie(problem.nonSensitiveItemsets());
        final Map<Integer, Double> weights = new HashMap<Integer, Double>();
        final int[] nonSensitiveCover = new int[1];
        List<Integer> order = new ArrayList<Integer>(scan.sensitiveTransactions());
        for (int tid : order) {
            nonSensitiveCover[0] = 0;
            nonSensitive.forEachContained(database.get(tid), problem.profits(),
                    (index, utility) -> nonSensitiveCover[0]++);
            weights.put(tid, weight(scan.itemsetsIn(tid).length, nonSensitiveCover[0]));
        }
        Collections.sort(order, (a, b) -> Double.compare(weights.get(b), weights.get(a)));
        return order;
    }

    /**
     * Eq. 1 of the paper: the sensitive weight of a transaction, SC(T) / (NSC(T) + 1). One is added to the
     * denominator because a sensitive transaction may contain no non-sensitive itemset.
     */
    static double weight(int sensitiveCover, int nonSensitiveCover) {
        return (double) sensitiveCover / (nonSensitiveCover + 1);
    }
}
