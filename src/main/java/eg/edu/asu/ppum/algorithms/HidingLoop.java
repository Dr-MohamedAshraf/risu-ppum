/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.algorithms;

import eg.edu.asu.ppum.data.Itemset;
import eg.edu.asu.ppum.data.Transaction;
import eg.edu.asu.ppum.data.TransactionDatabase;
import eg.edu.asu.ppum.data.UtilityTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The hiding procedure, Lines 10-33 of Algorithms 1 and 2 (Lines 16-39 of Algorithm 3). It hides the
 * sensitive itemsets one after another. For a sensitive itemset S, diff = u(S) - minUtil + 1 is the utility
 * that S must lose. The transactions that support S are visited in Weighted Sorting order and, while diff is
 * positive, the victim item of S is changed in each of them:
 * <ul>
 *   <li>if diff is at least u(victim, T), the victim is deleted from T and diff falls by u(S, T), all the
 *       utility that S had in T, because T no longer supports S;</li>
 *   <li>otherwise the quantity of the victim is lowered by ceil(diff / unit profit of the victim), the smallest
 *       whole reduction that is enough, and diff becomes 0. When that reduction would leave a quantity of 0,
 *       the victim is deleted instead.</li>
 * </ul>
 * After each change, the current utility of every sensitive itemset that contains the victim and is supported
 * by T is lowered by the utility it lost in T ("Update S_j", Lines 19 and 24). That is how hiding one itemset
 * can partly or fully hide the others.
 */
final class HidingLoop {

    private final TransactionDatabase database;
    private final UtilityTable profits;
    private final long minUtil;
    private final List<Itemset> itemsets;
    private final int[] victims;
    private final long[] currentUtility;
    private final Map<Integer, List<Integer>> itemsetsWithItem = new HashMap<Integer, List<Integer>>();

    /**
     * Prepares the procedure. The current utility of each sensitive itemset starts at its utility in the
     * database, and an index lists, for every item, the sensitive itemsets that contain it.
     */
    HidingLoop(TransactionDatabase database, HidingProblem problem, int[] victims) {
        this.database = database;
        this.profits = problem.profits();
        this.minUtil = problem.minUtil();
        this.itemsets = problem.sensitiveItemsets();
        this.victims = victims;
        this.currentUtility = new long[itemsets.size()];
        for (int index = 0; index < itemsets.size(); index++) {
            Itemset itemset = itemsets.get(index);
            currentUtility[index] = itemset.utility();
            for (int i = 0; i < itemset.size(); i++) {
                List<Integer> containing = itemsetsWithItem.get(itemset.get(i));
                if (containing == null) {
                    containing = new ArrayList<Integer>();
                    itemsetsWithItem.put(itemset.get(i), containing);
                }
                containing.add(index);
            }
        }
    }

    /**
     * Hides the sensitive itemsets in the given order (Line 10). For each itemset, the matching entry of
     * supportingTransactions lists the transactions that contain it, in Weighted Sorting order.
     */
    void hide(List<Integer> itemsetOrder, List<List<Integer>> supportingTransactions) {
        for (int index : itemsetOrder) {
            hideItemset(index, supportingTransactions.get(index));
        }
    }

    /**
     * Hides one sensitive itemset (Lines 11-32). It stops as soon as diff is no longer positive (Lines 13 and
     * 29-30), so an itemset that earlier changes already hid is left alone.
     */
    private void hideItemset(int index, List<Integer> transactions) {
        Itemset itemset = itemsets.get(index);
        int victim = victims[index];
        int unitProfit = profits.unitProfit(victim);
        long diff = currentUtility[index] - minUtil + 1;
        for (int tid : transactions) {
            if (diff <= 0) {
                return;
            }
            Transaction transaction = database.get(tid);
            if (!transaction.containsAll(itemset)) {
                continue;
            }
            int quantity = transaction.quantityOf(victim);
            if (diff >= (long) quantity * unitProfit) {
                diff -= deleteVictim(transaction, itemset, victim);
            } else {
                int decrease = (int) ((diff + unitProfit - 1) / unitProfit);
                if (decrease == quantity) {
                    diff -= deleteVictim(transaction, itemset, victim);
                } else {
                    reduceVictim(transaction, victim, decrease, unitProfit);
                    diff = 0;
                }
            }
        }
    }

    /**
     * Deletes the victim item from the transaction (Lines 16-19). Every sensitive itemset that contains the
     * victim and is supported by the transaction loses its whole utility there. Returns u(S, T), the utility
     * that the itemset being hidden had in the transaction.
     */
    private long deleteVictim(Transaction transaction, Itemset itemset, int victim) {
        long lostByItemset = transaction.utilityOf(itemset, profits);
        for (int other : itemsetsWithItem.get(victim)) {
            currentUtility[other] -= transaction.utilityOf(itemsets.get(other), profits);
        }
        transaction.remove(victim);
        return lostByItemset;
    }

    /**
     * Lowers the quantity of the victim item in the transaction (Lines 21-25). Every sensitive itemset that
     * contains the victim and is supported by the transaction loses decrease times the victim's unit profit.
     */
    private void reduceVictim(Transaction transaction, int victim, int decrease, int unitProfit) {
        long lost = (long) decrease * unitProfit;
        for (int other : itemsetsWithItem.get(victim)) {
            if (transaction.containsAll(itemsets.get(other))) {
                currentUtility[other] -= lost;
            }
        }
        transaction.setQuantity(victim, transaction.quantityOf(victim) - decrease);
    }
}
