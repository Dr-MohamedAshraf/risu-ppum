/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.algorithms;

import eg.edu.asu.ppum.data.Itemset;
import eg.edu.asu.ppum.data.UtilityTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The hiding task given to a sanitization algorithm together with the database: the unit profits, the minimum
 * utility threshold (minUtil), the sensitive high-utility itemsets (SHIs) that must be hidden, and the
 * non-sensitive high-utility itemsets (NHIs) that should survive. The utility of every itemset is its exact
 * utility in the original database.
 */
public final class HidingProblem {

    private final UtilityTable profits;
    private final long minUtil;
    private final List<Itemset> sensitiveItemsets;
    private final List<Itemset> nonSensitiveItemsets;

    /**
     * Creates a hiding task. The threshold must be positive, there must be at least one sensitive itemset, and
     * no itemset may be listed twice or be both sensitive and non-sensitive. The order of the sensitive
     * itemsets is kept: it decides which one is hidden first when two of them have the same priority.
     */
    public HidingProblem(UtilityTable profits, long minUtil, List<Itemset> sensitiveItemsets,
                         List<Itemset> nonSensitiveItemsets) {
        if (minUtil <= 0) {
            throw new IllegalArgumentException("the minimum utility threshold must be positive, not " + minUtil);
        }
        if (sensitiveItemsets.isEmpty()) {
            throw new IllegalArgumentException("there must be at least one sensitive itemset");
        }
        Set<Itemset> seen = new HashSet<Itemset>();
        for (Itemset itemset : sensitiveItemsets) {
            if (!seen.add(itemset)) {
                throw new IllegalArgumentException("sensitive itemset " + itemset + " is listed twice");
            }
        }
        for (Itemset itemset : nonSensitiveItemsets) {
            if (!seen.add(itemset)) {
                throw new IllegalArgumentException("itemset " + itemset + " is listed twice or is also sensitive");
            }
        }
        this.profits = profits;
        this.minUtil = minUtil;
        this.sensitiveItemsets = Collections.unmodifiableList(new ArrayList<Itemset>(sensitiveItemsets));
        this.nonSensitiveItemsets = Collections.unmodifiableList(new ArrayList<Itemset>(nonSensitiveItemsets));
    }

    /**
     * Returns the unit profit of every item.
     */
    public UtilityTable profits() {
        return profits;
    }

    /**
     * Returns the minimum utility threshold: an itemset is high-utility when its utility is at least this value.
     */
    public long minUtil() {
        return minUtil;
    }

    /**
     * Returns the sensitive itemsets to hide, each with its utility in the original database.
     */
    public List<Itemset> sensitiveItemsets() {
        return sensitiveItemsets;
    }

    /**
     * Returns the non-sensitive high-utility itemsets, each with its utility in the original database.
     */
    public List<Itemset> nonSensitiveItemsets() {
        return nonSensitiveItemsets;
    }
}
