/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.algorithms;

import eg.edu.asu.ppum.data.Itemset;
import eg.edu.asu.ppum.data.TransactionDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The procedure shared by SMRF, SLRF and SDIF (Algorithms 1-3 of the paper). The three algorithms differ only
 * in how they choose the victim item of each sensitive itemset, so this class runs every other step and asks
 * the subclass for that choice:
 * <ol>
 *   <li>find the sensitive transactions and compute RISU</li>
 *   <li>choose one victim item for every sensitive itemset ({@link #chooseVictims}</li>
 *   <li>sort the sensitive transactions with Weighted Sorting</li>
 *   <li>order the sensitive itemsets by decreasing RISU of their victim items ({@link #orderSensitiveItemsets}</li>
 *   <li>hide them one after another </li>
 * </ol>
 * A new RISU-based variant only has to implement {@link #name} and {@link #chooseVictims}. It may also override
 * {@link #orderSensitiveItemsets} to try another processing order.
 */
public abstract class RisuBasedAlgorithm implements SanitizationAlgorithm {

    /**
     * Runs the five steps of the procedure on the database.
     */
    @Override
    public final void sanitize(TransactionDatabase database, HidingProblem problem) {
        RisuCalculator scan = RisuCalculator.scan(database, problem);
        int[] victims = chooseVictims(problem, scan.risu());
        checkVictims(problem, victims);
        List<Integer> transactionOrder = WeightedSorting.sort(database, scan, problem);
        List<Integer> itemsetOrder = orderSensitiveItemsets(problem, victims, scan.risu());
        checkOrder(problem, itemsetOrder);
        new HidingLoop(database, problem, victims)
                .hide(itemsetOrder, supportingTransactions(problem, scan, transactionOrder));
    }

    /**
     * Chooses the victim item of every sensitive itemset. Entry i of the result is the victim of
     * {@code problem.sensitiveItemsets().get(i)} and must be one of its items.
     */
    protected abstract int[] chooseVictims(HidingProblem problem, Map<Integer, Long> risu);

    /**
     * Returns the item of an itemset with the largest RISU. When several items share that value, the one
     * written first wins.
     */
    protected static int itemWithLargestRisu(Itemset itemset, Map<Integer, Long> risu) {
        int best = itemset.get(0);
        for (int i = 1; i < itemset.size(); i++) {
            if (risu.get(itemset.get(i)) > risu.get(best)) {
                best = itemset.get(i);
            }
        }
        return best;
    }

    /**
     * Returns, among the candidate items, the one with the smallest RISU. When several items share that value,
     * the first candidate wins.
     */
    protected static int itemWithSmallestRisu(List<Integer> candidates, Map<Integer, Long> risu) {
        int best = candidates.get(0);
        for (int candidate : candidates) {
            if (risu.get(candidate) < risu.get(best)) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Returns the items of an itemset as a list, in the order in which they were written.
     */
    protected static List<Integer> itemsOf(Itemset itemset) {
        List<Integer> items = new ArrayList<Integer>(itemset.size());
        for (int i = 0; i < itemset.size(); i++) {
            items.add(itemset.get(i));
        }
        return items;
    }

    /**
     * Returns the order in which the sensitive itemsets are hidden, as indices into
     * {@code problem.sensitiveItemsets()}: by decreasing RISU of their victim items.
     * The sort is stable, so itemsets with equal values keep the order in which they were given.
     * The result must list every index exactly once.
     */
    protected List<Integer> orderSensitiveItemsets(HidingProblem problem, final int[] victims,
                                                   final Map<Integer, Long> risu) {
        List<Integer> order = new ArrayList<Integer>(victims.length);
        for (int index = 0; index < victims.length; index++) {
            order.add(index);
        }
        Collections.sort(order, (a, b) -> Long.compare(risu.get(victims[b]), risu.get(victims[a])));
        return order;
    }

    /**
     * Lists, for every sensitive itemset, the transactions that contain it, in Weighted Sorting order.
     */
    private static List<List<Integer>> supportingTransactions(HidingProblem problem, RisuCalculator scan,
                                                              List<Integer> transactionOrder) {
        List<List<Integer>> supporting = new ArrayList<List<Integer>>();
        for (int index = 0; index < problem.sensitiveItemsets().size(); index++) {
            supporting.add(new ArrayList<Integer>());
        }
        for (int tid : transactionOrder) {
            for (int index : scan.itemsetsIn(tid)) {
                supporting.get(index).add(tid);
            }
        }
        return supporting;
    }

    /**
     * Checks that the processing order lists every sensitive itemset exactly once.
     */
    private static void checkOrder(HidingProblem problem, List<Integer> order) {
        int count = problem.sensitiveItemsets().size();
        boolean[] seen = new boolean[count];
        if (order == null || order.size() != count) {
            throw new IllegalStateException("orderSensitiveItemsets must list every sensitive itemset once");
        }
        for (int index : order) {
            if (index < 0 || index >= count || seen[index]) {
                throw new IllegalStateException("orderSensitiveItemsets must list every sensitive itemset once");
            }
            seen[index] = true;
        }
    }

    /**
     * Checks that the subclass returned one victim per sensitive itemset, each taken from its own itemset.
     */
    private static void checkVictims(HidingProblem problem, int[] victims) {
        List<Itemset> itemsets = problem.sensitiveItemsets();
        if (victims == null || victims.length != itemsets.size()) {
            throw new IllegalStateException("chooseVictims must return one victim per sensitive itemset");
        }
        for (int index = 0; index < victims.length; index++) {
            if (!itemsets.get(index).contains(victims[index])) {
                throw new IllegalStateException("victim " + victims[index] + " is not an item of the sensitive itemset "
                        + itemsets.get(index));
            }
        }
    }
}
