/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.algorithms;

import eg.edu.asu.ppum.data.Itemset;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SDIF, "Selecting the most Desirable Item First" (Algorithm 3 of the paper). Instead of RISU alone, it judges
 * the items of each sensitive itemset by three criteria: the sensitive cover SC, the non-sensitive cover NSC and
 * RISU. The most desirable victim has both the smallest NSC and the largest SC within its itemset, because
 * changing it hides many sensitive itemsets while harming few non-sensitive ones. When no item is best on both
 * covers, the item with the smallest NSC is chosen. In both cases RISU breaks ties: the item with the smallest
 * RISU wins, and after that the item written first.
 */
public final class SDIF extends RisuBasedAlgorithm {

    /**
     * Returns "SDIF".
     */
    @Override
    public String name() {
        return "SDIF";
    }

    /**
     * chooses the most desirable item of each sensitive itemset as its victim.
     */
    @Override
    protected int[] chooseVictims(HidingProblem problem, Map<Integer, Long> risu) {
        ItemCovers covers = new ItemCovers(problem);
        List<Itemset> itemsets = problem.sensitiveItemsets();
        int[] victims = new int[itemsets.size()];
        for (int index = 0; index < victims.length; index++) {
            Itemset itemset = itemsets.get(index);
            List<Integer> fewestNonSensitive = itemsWithSmallestNonSensitiveCover(itemset, covers);
            List<Integer> mostSensitive = itemsWithLargestSensitiveCover(itemset, covers);
            List<Integer> desirable = new ArrayList<Integer>(fewestNonSensitive);
            desirable.retainAll(mostSensitive);
            victims[index] = itemWithSmallestRisu(desirable.isEmpty() ? fewestNonSensitive : desirable, risu);
        }
        return victims;
    }

    /**
     * Returns the items of an itemset that have the smallest non-sensitive cover, in the order written.
     */
    private static List<Integer> itemsWithSmallestNonSensitiveCover(Itemset itemset, ItemCovers covers) {
        List<Integer> best = new ArrayList<Integer>();
        int smallest = Integer.MAX_VALUE;
        for (int item : itemsOf(itemset)) {
            int cover = covers.nonSensitiveCover(item);
            if (cover < smallest) {
                smallest = cover;
                best.clear();
            }
            if (cover == smallest) {
                best.add(item);
            }
        }
        return best;
    }

    /**
     * Returns the items of an itemset that have the largest sensitive cover, in the order written.
     */
    private static List<Integer> itemsWithLargestSensitiveCover(Itemset itemset, ItemCovers covers) {
        List<Integer> best = new ArrayList<Integer>();
        int largest = Integer.MIN_VALUE;
        for (int item : itemsOf(itemset)) {
            int cover = covers.sensitiveCover(item);
            if (cover > largest) {
                largest = cover;
                best.clear();
            }
            if (cover == largest) {
                best.add(item);
            }
        }
        return best;
    }
}
