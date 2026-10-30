/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.algorithms;

import eg.edu.asu.ppum.data.TransactionDatabase;

/**
 * A privacy preserving utility mining (PPUM) algorithm: it changes a database so that its sensitive
 * high-utility itemsets can no longer be mined, while trying to keep its other high-utility itemsets.
 *
 * <p>Implement this interface to run your own algorithm in this framework and compare it with SMRF, SLRF and
 * SDIF on exactly the same inputs. The framework gives every algorithm its own copy of the database, measures
 * its runtime and peak memory, and computes the same side-effect metrics for all of them.</p>
 */
public interface SanitizationAlgorithm {

    /**
     * Returns the short name used in reports and output folders, for example "SDIF".
     */
    String name();

    /**
     * Sanitizes the database in place. The database is a private copy made for this run, and it should be
     * changed only through the methods of {@link eg.edu.asu.ppum.data.Transaction}. When the method returns,
     * every sensitive itemset of the problem should have a utility below the minimum utility threshold.
     */
    void sanitize(TransactionDatabase database, HidingProblem problem);
}
