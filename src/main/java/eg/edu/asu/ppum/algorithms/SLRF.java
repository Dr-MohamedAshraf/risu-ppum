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
 * SLRF, "Selecting the Least Real item sensitive utility First" (Algorithm 2 of the paper). It works like SMRF,
 * except that the victim item of each sensitive itemset is its item with the smallest RISU: the item whose
 * changes touch the least sensitive utility, which tends to keep more of the remaining data intact.
 */
public final class SLRF extends RisuBasedAlgorithm {

    /**
     * Returns "SLRF".
     */
    @Override
    public String name() {
        return "SLRF";
    }

    /**
     * the victim of each sensitive itemset is its item with the smallest RISU.
     */
    @Override
    protected int[] chooseVictims(HidingProblem problem, Map<Integer, Long> risu) {
        List<Itemset> itemsets = problem.sensitiveItemsets();
        int[] victims = new int[itemsets.size()];
        for (int index = 0; index < victims.length; index++) {
            victims[index] = itemWithSmallestRisu(itemsOf(itemsets.get(index)), risu);
        }
        return victims;
    }
}
