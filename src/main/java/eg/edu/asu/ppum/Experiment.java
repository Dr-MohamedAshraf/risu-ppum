/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum;

import eg.edu.asu.ppum.algorithms.HidingProblem;
import eg.edu.asu.ppum.algorithms.SanitizationAlgorithm;
import eg.edu.asu.ppum.data.DataFormatException;
import eg.edu.asu.ppum.data.Itemset;
import eg.edu.asu.ppum.data.ItemsetFile;
import eg.edu.asu.ppum.data.ItemsetTrie;
import eg.edu.asu.ppum.data.TransactionDatabase;
import eg.edu.asu.ppum.data.UtilityTable;
import eg.edu.asu.ppum.evaluation.MemoryMonitor;
import eg.edu.asu.ppum.evaluation.RunResult;
import eg.edu.asu.ppum.evaluation.SideEffects;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A privacy preserving utility mining experiment on one database: its transactions, the unit profits, the
 * minimum utility threshold and the high-utility itemsets (HUIs) of the database, mined beforehand with any
 * high-utility itemset mining algorithm. The experiment checks that these inputs belong together, provides the
 * sensitive itemsets to hide (read from a file, or drawn at random as in Section 6 of the paper), and runs
 * sanitization algorithms on separate copies of the database, so every algorithm works on exactly the same
 * inputs and is measured in the same way.
 */
public final class Experiment {

    private final String databaseName;
    private final TransactionDatabase database;
    private final UtilityTable profits;
    private final long totalUtility;
    private final long minUtil;
    private final List<Itemset> highUtilityItemsets;
    private final List<String> notes;

    /**
     * Stores checked inputs.
     */
    private Experiment(String databaseName, TransactionDatabase database, UtilityTable profits, long totalUtility,
                       long minUtil, List<Itemset> highUtilityItemsets, List<String> notes) {
        this.databaseName = databaseName;
        this.database = database;
        this.profits = profits;
        this.totalUtility = totalUtility;
        this.minUtil = minUtil;
        this.highUtilityItemsets = Collections.unmodifiableList(highUtilityItemsets);
        this.notes = notes;
    }

    /**
     * Loads and checks the inputs of an experiment. The minimum utility is either a percentage of the total
     * utility of the database, such as "21%" as in Table 10 of the paper, or an absolute value such as "70".
     * The utility given for every high-utility itemset must equal its utility in the database, which shows that
     * the itemsets were mined from this database and profit file. Itemsets below the threshold are ignored, so
     * one file mined at a low threshold can serve several higher thresholds.
     */
    public static Experiment load(Path databaseFile, Path profitsFile, Path highUtilityItemsetsFile, String minUtil)
            throws IOException {
        UtilityTable profits = UtilityTable.read(profitsFile);
        TransactionDatabase database = TransactionDatabase.read(databaseFile, profits);
        long totalUtility = database.totalUtility(profits);
        long threshold = parseMinUtil(minUtil, totalUtility);
        List<String> notes = new ArrayList<String>();
        List<Itemset> itemsets = withoutDuplicates(ItemsetFile.read(highUtilityItemsetsFile), highUtilityItemsetsFile,
                notes);
        long[] utilities = new ItemsetTrie(itemsets).utilities(database, profits);
        List<Itemset> highUtility = new ArrayList<Itemset>();
        for (int index = 0; index < itemsets.size(); index++) {
            Itemset itemset = itemsets.get(index);
            checkUtility(itemset, utilities[index], highUtilityItemsetsFile);
            if (itemset.utility() >= threshold) {
                highUtility.add(itemset);
            }
        }
        if (highUtility.size() < itemsets.size()) {
            notes.add((itemsets.size() - highUtility.size()) + " itemsets of " + highUtilityItemsetsFile
                    + " are below minUtil " + threshold + " and were ignored");
        }
        if (highUtility.isEmpty()) {
            throw new DataFormatException(highUtilityItemsetsFile + ": no itemset reaches minUtil " + threshold);
        }
        return new Experiment(databaseFile.toString(), database, profits, totalUtility, threshold, highUtility, notes);
    }

    /**
     * Converts a minimum utility setting into an absolute threshold. A value ending with "%" is a percentage of
     * the total utility, rounded down as in the original code; any other value must be a whole number of at
     * least 1.
     */
    public static long parseMinUtil(String text, long totalUtility) {
        String value = text.trim();
        long threshold;
        try {
            if (value.endsWith("%")) {
                BigDecimal percent = new BigDecimal(value.substring(0, value.length() - 1).trim());
                if (percent.signum() <= 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new IllegalArgumentException("minUtil must be a percentage above 0 and at most 100%, found '"
                            + text + "'");
                }
                threshold = percent.multiply(BigDecimal.valueOf(totalUtility))
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR).longValueExact();
            } else {
                threshold = Long.parseLong(value);
            }
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("minUtil must be a percentage of the total utility, such as 21%, or a"
                    + " whole number, such as 70; found '" + text + "'");
        }
        if (threshold < 1) {
            throw new IllegalArgumentException("minUtil must be at least 1, but '" + text + "' gives " + threshold);
        }
        return threshold;
    }

    /**
     * Reads the sensitive itemsets to hide from a file in the same format as the high-utility itemsets; the
     * easiest way to write it is to copy lines of the high-utility itemset file. Every sensitive itemset must
     * have its correct utility and reach the threshold. The order of the file is kept, and it decides which
     * itemset is hidden first when two of them have the same priority.
     */
    public List<Itemset> readSensitiveItemsets(Path file) throws IOException {
        List<Itemset> itemsets = withoutDuplicates(ItemsetFile.read(file), file, notes);
        if (itemsets.isEmpty()) {
            throw new DataFormatException(file + ": the file contains no sensitive itemsets");
        }
        long[] utilities = new ItemsetTrie(itemsets).utilities(database, profits);
        Set<Itemset> known = new HashSet<Itemset>(highUtilityItemsets);
        for (int index = 0; index < itemsets.size(); index++) {
            Itemset itemset = itemsets.get(index);
            checkUtility(itemset, utilities[index], file);
            if (itemset.utility() < minUtil) {
                throw new DataFormatException(file + ": sensitive itemset '" + itemset + "' is below minUtil " + minUtil
                        + "; only high-utility itemsets can be hidden");
            }
            if (!known.contains(itemset)) {
                notes.add("sensitive itemset '" + itemset + "' is missing from the high-utility itemset file, which may"
                        + " be incomplete");
            }
            if (itemset.size() < 2) {
                notes.add("sensitive itemset '" + itemset + "' has a single item; the paper uses itemsets of at least"
                        + " two items");
            }
        }
        return itemsets;
    }

    /**
     * Draws sensitive itemsets at random among the high-utility itemsets with at least two items, as in the
     * experiments of Section 6 of the paper. The candidates are put in a fixed order first, so the same seed
     * always gives the same itemsets, in the same order, whichever miner produced the high-utility itemsets.
     */
    public List<Itemset> drawSensitiveItemsets(int count, long seed) {
        List<Itemset> candidates = new ArrayList<Itemset>();
        for (Itemset itemset : highUtilityItemsets) {
            if (itemset.size() >= 2) {
                candidates.add(itemset);
            }
        }
        Collections.sort(candidates);
        if (count < 1 || count > candidates.size()) {
            throw new IllegalArgumentException("cannot draw " + count + " sensitive itemsets: there are "
                    + candidates.size() + " high-utility itemsets with at least two items");
        }
        Random random = new Random(seed);
        for (int index = 0; index < count; index++) {
            Collections.swap(candidates, index, index + random.nextInt(candidates.size() - index));
        }
        return new ArrayList<Itemset>(candidates.subList(0, count));
    }

    /**
     * Runs one algorithm on a fresh copy of the database and measures it. Only the sanitization itself is timed
     * and monitored for memory; the side effects are measured afterwards. The original database is not changed,
     * so several algorithms can be run one after another on the same experiment.
     */
    public RunResult run(SanitizationAlgorithm algorithm, List<Itemset> sensitiveItemsets) {
        Set<Itemset> sensitive = new HashSet<Itemset>(sensitiveItemsets);
        List<Itemset> nonSensitive = new ArrayList<Itemset>();
        for (Itemset itemset : highUtilityItemsets) {
            if (!sensitive.contains(itemset)) {
                nonSensitive.add(itemset);
            }
        }
        HidingProblem problem = new HidingProblem(profits, minUtil, sensitiveItemsets, nonSensitive);
        TransactionDatabase copy = database.copy();
        long elapsed;
        double peakMemory;
        MemoryMonitor memory = MemoryMonitor.start();
        long start = System.nanoTime();
        try {
            algorithm.sanitize(copy, problem);
        } finally {
            elapsed = System.nanoTime() - start;
            peakMemory = memory.stopAndGetPeakMegabytes();
        }
        SideEffects sideEffects = SideEffects.measure(database, copy, problem);
        return new RunResult(algorithm.name(), copy, sideEffects, elapsed / 1_000_000.0, peakMemory);
    }

    /**
     * Returns the notes collected while checking the inputs, such as ignored itemsets, and forgets them, so
     * each note is reported once.
     */
    public List<String> takeNotes() {
        List<String> taken = new ArrayList<String>(notes);
        notes.clear();
        return taken;
    }

    /**
     * Returns the path of the database file, as given.
     */
    public String databaseName() {
        return databaseName;
    }

    /**
     * Returns the original database, which runs never change.
     */
    public TransactionDatabase database() {
        return database;
    }

    /**
     * Returns the unit profits.
     */
    public UtilityTable profits() {
        return profits;
    }

    /**
     * Returns the total utility of the database.
     */
    public long totalUtility() {
        return totalUtility;
    }

    /**
     * Returns the absolute minimum utility threshold.
     */
    public long minUtil() {
        return minUtil;
    }

    /**
     * Returns the high-utility itemsets of the database at the threshold, in file order.
     */
    public List<Itemset> highUtilityItemsets() {
        return highUtilityItemsets;
    }

    /**
     * Checks that the utility written for an itemset equals its utility in the database.
     */
    private static void checkUtility(Itemset itemset, long utilityInDatabase, Path file) {
        if (itemset.utility() != utilityInDatabase) {
            throw new DataFormatException(file + ": itemset '" + itemset + "' has utility " + itemset.utility()
                    + " in the file but " + utilityInDatabase + " in the database; the itemsets must be mined from this"
                    + " database and profit file");
        }
    }

    /**
     * Removes repeated itemsets, keeping the first occurrence of each, and notes how many were removed.
     */
    private static List<Itemset> withoutDuplicates(List<Itemset> itemsets, Path file, List<String> notes) {
        Set<Itemset> unique = new LinkedHashSet<Itemset>(itemsets);
        if (unique.size() < itemsets.size()) {
            notes.add((itemsets.size() - unique.size()) + " repeated itemsets in " + file + " were ignored");
        }
        return new ArrayList<Itemset>(unique);
    }
}
