/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.algorithms;

import eg.edu.asu.ppum.data.Itemset;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The sensitive cover SC(x) and the non-sensitive cover NSC(x) of every item (Definitions 8 and 10): the number
 * of sensitive itemsets and the number of non-sensitive high-utility itemsets that contain the item. SDIF uses
 * them to find desirable victims: an item with a high SC and a low NSC hides many sensitive itemsets and harms
 * few non-sensitive ones when it is changed.
 */
public final class ItemCovers {

    private final Map<Integer, Integer> sensitiveCover;
    private final Map<Integer, Integer> nonSensitiveCover;

    /**
     * Counts the covers of all items of the sensitive and non-sensitive itemsets of a hiding task.
     */
    public ItemCovers(HidingProblem problem) {
        this.sensitiveCover = count(problem.sensitiveItemsets());
        this.nonSensitiveCover = count(problem.nonSensitiveItemsets());
    }

    /**
     * Returns SC(x), the number of sensitive itemsets that contain the item.
     */
    public int sensitiveCover(int item) {
        Integer cover = sensitiveCover.get(item);
        return cover == null ? 0 : cover;
    }

    /**
     * Returns NSC(x), the number of non-sensitive high-utility itemsets that contain the item.
     */
    public int nonSensitiveCover(int item) {
        Integer cover = nonSensitiveCover.get(item);
        return cover == null ? 0 : cover;
    }

    /**
     * Counts, for every item, how many of the itemsets contain it.
     */
    private static Map<Integer, Integer> count(List<Itemset> itemsets) {
        Map<Integer, Integer> counts = new HashMap<Integer, Integer>();
        for (Itemset itemset : itemsets) {
            for (int i = 0; i < itemset.size(); i++) {
                Integer previous = counts.get(itemset.get(i));
                counts.put(itemset.get(i), previous == null ? 1 : previous + 1);
            }
        }
        return counts;
    }
}
