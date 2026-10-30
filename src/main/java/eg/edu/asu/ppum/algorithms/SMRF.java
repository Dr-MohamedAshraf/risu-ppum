/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.algorithms;

import eg.edu.asu.ppum.data.Itemset;

import java.util.List;
import java.util.Map;

/**
 * SMRF, "Selecting the Most Real item sensitive utility First" (Algorithm 1 of the paper). The victim item of
 * each sensitive itemset is its item with the largest RISU: the item that carries the most utility in the
 * sensitive transactions, so the itemset can be hidden with few changes.
 */
public final class SMRF extends RisuBasedAlgorithm {

    /**
     * Returns "SMRF".
     */
    @Override
    public String name() {
        return "SMRF";
    }

    /**
     * the victim of each sensitive itemset is its item with the largest RISU.
     */
    @Override
    protected int[] chooseVictims(HidingProblem problem, Map<Integer, Long> risu) {
        List<Itemset> itemsets = problem.sensitiveItemsets();
        int[] victims = new int[itemsets.size()];
        for (int index = 0; index < victims.length; index++) {
            victims[index] = itemWithLargestRisu(itemsets.get(index), risu);
        }
        return victims;
    }
}
