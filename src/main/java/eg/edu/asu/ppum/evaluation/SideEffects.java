/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.evaluation;

import eg.edu.asu.ppum.algorithms.HidingProblem;
import eg.edu.asu.ppum.data.Itemset;
import eg.edu.asu.ppum.data.ItemsetTrie;
import eg.edu.asu.ppum.data.Transaction;
import eg.edu.asu.ppum.data.TransactionDatabase;
import eg.edu.asu.ppum.data.UtilityTable;

import java.util.ArrayList;
import java.util.List;

/**
 * The side effects of one sanitization, measured with the metrics of Section 6.1 of the paper: hiding failure
 * (HF, Eq. 3), artificial cost (AC, Eq. 4), missing cost (MC, Eq. 5), itemset utility similarity (IUS, Eq. 6),
 * dataset utility similarity (DUS, Eq. 7) and transaction modification ratio (TMR, Eq. 8).
 *
 * <p>The values are exact and need no new mining. The high-utility itemsets of the original database are
 * known, so their utilities in the sanitized database are recomputed directly. When a sanitization only deletes
 * items and lowers quantities, as SMRF, SLRF and SDIF do, no itemset can gain utility, so the high-utility
 * itemsets of the sanitized database are exactly those original ones that are still above the threshold. If an
 * algorithm adds items or raises quantities, new high-utility itemsets could appear that only mining would find;
 * AC and IUS are then reported as not available.</p>
 */
public final class SideEffects {

    private final int sensitiveItemsets;
    private final int failedSensitiveItemsets;
    private final int nonSensitiveItemsets;
    private final int lostNonSensitiveItemsets;
    private final boolean onlyReductions;
    private final long utilityOfItemsetsBefore;
    private final long utilityOfItemsetsAfter;
    private final long databaseUtilityBefore;
    private final long databaseUtilityAfter;
    private final int transactions;
    private final int modifiedTransactions;

    /**
     * Stores the counts and utilities from which the metrics are computed.
     */
    private SideEffects(int sensitiveItemsets, int failedSensitiveItemsets, int nonSensitiveItemsets,
                        int lostNonSensitiveItemsets, boolean onlyReductions, long utilityOfItemsetsBefore,
                        long utilityOfItemsetsAfter, long databaseUtilityBefore, long databaseUtilityAfter,
                        int transactions, int modifiedTransactions) {
        this.sensitiveItemsets = sensitiveItemsets;
        this.failedSensitiveItemsets = failedSensitiveItemsets;
        this.nonSensitiveItemsets = nonSensitiveItemsets;
        this.lostNonSensitiveItemsets = lostNonSensitiveItemsets;
        this.onlyReductions = onlyReductions;
        this.utilityOfItemsetsBefore = utilityOfItemsetsBefore;
        this.utilityOfItemsetsAfter = utilityOfItemsetsAfter;
        this.databaseUtilityBefore = databaseUtilityBefore;
        this.databaseUtilityAfter = databaseUtilityAfter;
        this.transactions = transactions;
        this.modifiedTransactions = modifiedTransactions;
    }

    /**
     * Measures the side effects of a sanitization by comparing the sanitized database with the original one.
     * Only the modified transactions are examined again: the utility of a high-utility itemset after
     * sanitization is its original utility, minus what it had in the modified transactions, plus what it still
     * has there.
     */
    public static SideEffects measure(TransactionDatabase original, TransactionDatabase sanitized,
                                      HidingProblem problem) {
        if (original.size() != sanitized.size()) {
            throw new IllegalArgumentException("the sanitized database must keep one line per original transaction");
        }
        UtilityTable profits = problem.profits();
        List<Transaction> modifiedBefore = new ArrayList<Transaction>();
        List<Transaction> modifiedAfter = new ArrayList<Transaction>();
        boolean onlyReductions = true;
        long databaseUtilityBefore = 0;
        long databaseUtilityAfter = 0;
        for (int tid = 0; tid < original.size(); tid++) {
            Transaction before = original.get(tid);
            Transaction after = sanitized.get(tid);
            databaseUtilityBefore += before.transactionUtility(profits);
            databaseUtilityAfter += after.transactionUtility(profits);
            if (!before.hasSameContentAs(after)) {
                modifiedBefore.add(before);
                modifiedAfter.add(after);
                onlyReductions &= after.isReductionOf(before);
            }
        }
        List<Itemset> itemsets = new ArrayList<Itemset>(problem.sensitiveItemsets());
        itemsets.addAll(problem.nonSensitiveItemsets());
        ItemsetTrie trie = new ItemsetTrie(itemsets);
        long[] lost = trie.utilities(modifiedBefore, profits);
        long[] kept = trie.utilities(modifiedAfter, profits);
        int sensitive = problem.sensitiveItemsets().size();
        int failed = 0;
        int lostNonSensitive = 0;
        long utilityBefore = 0;
        long utilityAfter = 0;
        for (int index = 0; index < itemsets.size(); index++) {
            long before = itemsets.get(index).utility();
            long after = before - lost[index] + kept[index];
            boolean stillHighUtility = after >= problem.minUtil();
            if (index < sensitive && stillHighUtility) {
                failed++;
            }
            if (index >= sensitive && !stillHighUtility) {
                lostNonSensitive++;
            }
            utilityBefore += before;
            if (stillHighUtility) {
                utilityAfter += after;
            }
        }
        return new SideEffects(sensitive, failed, itemsets.size() - sensitive, lostNonSensitive, onlyReductions,
                utilityBefore, utilityAfter, databaseUtilityBefore, databaseUtilityAfter, original.size(),
                modifiedBefore.size());
    }

    /**
     * HF, Eq. 3: the fraction of sensitive itemsets that are still high-utility after sanitization. 0 means
     * that every sensitive itemset was hidden.
     */
    public double hidingFailure() {
        return (double) failedSensitiveItemsets / sensitiveItemsets;
    }

    /**
     * AC, Eq. 4: the fraction of the high-utility itemsets of the sanitized database that are fake, that is, not
     * high-utility in the original database. It is 0 when the sanitization only deleted items and lowered
     * quantities, and not available (NaN) otherwise, because finding new high-utility itemsets would require
     * mining.
     */
    public double artificialCost() {
        return onlyReductions ? 0.0 : Double.NaN;
    }

    /**
     * MC, Eq. 5: the fraction of non-sensitive high-utility itemsets that are no longer high-utility after
     * sanitization. Not available (NaN) when there are no non-sensitive high-utility itemsets.
     */
    public double missingCost() {
        return nonSensitiveItemsets == 0 ? Double.NaN : (double) lostNonSensitiveItemsets / nonSensitiveItemsets;
    }

    /**
     * IUS, Eq. 6: the total utility of the high-utility itemsets of the sanitized database divided by the total
     * utility of the high-utility itemsets of the original one. Not available (NaN) when the sanitization added
     * items or raised quantities, for the reason given for {@link #artificialCost}.
     */
    public double itemsetUtilitySimilarity() {
        return onlyReductions ? (double) utilityOfItemsetsAfter / utilityOfItemsetsBefore : Double.NaN;
    }

    /**
     * DUS, Eq. 7: the total utility of the sanitized database divided by the total utility of the original one.
     */
    public double datasetUtilitySimilarity() {
        return (double) databaseUtilityAfter / databaseUtilityBefore;
    }

    /**
     * TMR, Eq. 8: the fraction of transactions that the sanitization changed.
     */
    public double transactionModificationRatio() {
        return (double) modifiedTransactions / transactions;
    }

    /**
     * Returns the number of sensitive itemsets.
     */
    public int sensitiveItemsets() {
        return sensitiveItemsets;
    }

    /**
     * Returns the number of sensitive itemsets that are still high-utility after sanitization.
     */
    public int failedSensitiveItemsets() {
        return failedSensitiveItemsets;
    }

    /**
     * Returns the number of non-sensitive high-utility itemsets of the original database.
     */
    public int nonSensitiveItemsets() {
        return nonSensitiveItemsets;
    }

    /**
     * Returns the number of non-sensitive high-utility itemsets that are no longer high-utility.
     */
    public int lostNonSensitiveItemsets() {
        return lostNonSensitiveItemsets;
    }

    /**
     * Tells whether the sanitization only deleted items and lowered quantities.
     */
    public boolean onlyReductions() {
        return onlyReductions;
    }

    /**
     * Returns the number of transactions in the database.
     */
    public int transactions() {
        return transactions;
    }

    /**
     * Returns the number of transactions that the sanitization changed.
     */
    public int modifiedTransactions() {
        return modifiedTransactions;
    }
}
