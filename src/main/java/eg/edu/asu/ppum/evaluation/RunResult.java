/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.evaluation;

import eg.edu.asu.ppum.data.TransactionDatabase;

import java.util.Locale;

/**
 * The outcome of running one algorithm: the sanitized database, its side effects, the runtime of the
 * sanitization and the peak memory. It also formats the report printed after each run and the CSV row used to
 * compare runs.
 */
public final class RunResult {

    private static final String[] CSV_COLUMNS = {"database", "min_util", "sensitive_itemsets_source",
        "sensitive_itemsets", "algorithm", "hf_percent", "ac_percent", "mc_percent", "ius_percent", "dus_percent",
        "tmr_percent", "runtime_ms", "peak_memory_mb", "failed_sensitive_itemsets", "nonsensitive_itemsets",
        "lost_nonsensitive_itemsets", "transactions", "modified_transactions"};

    private final String algorithm;
    private final TransactionDatabase sanitizedDatabase;
    private final SideEffects sideEffects;
    private final double runtimeMillis;
    private final double peakMemoryMegabytes;

    /**
     * Creates the result of one run.
     */
    public RunResult(String algorithm, TransactionDatabase sanitizedDatabase, SideEffects sideEffects,
                     double runtimeMillis, double peakMemoryMegabytes) {
        this.algorithm = algorithm;
        this.sanitizedDatabase = sanitizedDatabase;
        this.sideEffects = sideEffects;
        this.runtimeMillis = runtimeMillis;
        this.peakMemoryMegabytes = peakMemoryMegabytes;
    }

    /**
     * Returns the name of the algorithm.
     */
    public String algorithm() {
        return algorithm;
    }

    /**
     * Returns the sanitized database.
     */
    public TransactionDatabase sanitizedDatabase() {
        return sanitizedDatabase;
    }

    /**
     * Returns the side effects of the sanitization.
     */
    public SideEffects sideEffects() {
        return sideEffects;
    }

    /**
     * Returns the time the algorithm took to sanitize the database, in milliseconds, without reading the inputs
     * and without measuring the side effects.
     */
    public double runtimeMillis() {
        return runtimeMillis;
    }

    /**
     * Returns the peak heap memory in use while the algorithm ran, in megabytes.
     */
    public double peakMemoryMegabytes() {
        return peakMemoryMegabytes;
    }

    /**
     * Returns the report printed after the run: every metric of the paper with the counts behind it, the
     * runtime, the peak memory and where the sanitized database was written.
     */
    public String report(String sanitizedFile) {
        SideEffects effects = sideEffects;
        StringBuilder text = new StringBuilder();
        text.append(algorithm).append(System.lineSeparator());
        line(text, "Hiding failure (HF)", percent(effects.hidingFailure()),
                effects.failedSensitiveItemsets() + " of " + effects.sensitiveItemsets()
                        + " sensitive itemsets still high-utility");
        line(text, "Artificial cost (AC)", percent(effects.artificialCost()), effects.onlyReductions()
                ? "only deletions and decreases, so no new high-utility itemsets"
                : "items were added or raised: mine the sanitized database to measure AC");
        line(text, "Missing cost (MC)", percent(effects.missingCost()),
                effects.lostNonSensitiveItemsets() + " of " + effects.nonSensitiveItemsets()
                        + " non-sensitive high-utility itemsets lost");
        line(text, "Itemset utility similarity (IUS)", percent(effects.itemsetUtilitySimilarity()), "");
        line(text, "Dataset utility similarity (DUS)", percent(effects.datasetUtilitySimilarity()), "");
        line(text, "Transaction modification ratio (TMR)", percent(effects.transactionModificationRatio()),
                effects.modifiedTransactions() + " of " + effects.transactions() + " transactions modified");
        line(text, "Runtime", String.format(Locale.ROOT, "%.2f ms", runtimeMillis), "");
        line(text, "Peak memory", String.format(Locale.ROOT, "%.2f MB", peakMemoryMegabytes), "");
        if (sanitizedFile != null) {
            text.append(String.format(Locale.ROOT, "  %-38s %s", "Sanitized database", sanitizedFile))
                    .append(System.lineSeparator());
        }
        return text.toString();
    }

    /**
     * Returns the header line of the CSV file of results.
     */
    public static String csvHeader() {
        return String.join(",", CSV_COLUMNS);
    }

    /**
     * Returns this run as one CSV row. The database, the threshold and the source of the sensitive itemsets
     * describe the inputs, so that rows from different experiments can share one file.
     */
    public String csvRow(String database, long minUtil, String sensitiveSource) {
        SideEffects effects = sideEffects;
        String[] values = {csv(database), Long.toString(minUtil), csv(sensitiveSource),
            Integer.toString(effects.sensitiveItemsets()), csv(algorithm), number(effects.hidingFailure() * 100),
            number(effects.artificialCost() * 100), number(effects.missingCost() * 100),
            number(effects.itemsetUtilitySimilarity() * 100), number(effects.datasetUtilitySimilarity() * 100),
            number(effects.transactionModificationRatio() * 100), number(runtimeMillis), number(peakMemoryMegabytes),
            Integer.toString(effects.failedSensitiveItemsets()), Integer.toString(effects.nonSensitiveItemsets()),
            Integer.toString(effects.lostNonSensitiveItemsets()), Integer.toString(effects.transactions()),
            Integer.toString(effects.modifiedTransactions())};
        return String.join(",", values);
    }

    /**
     * Appends one aligned line of the report: a label, a value and an optional explanation.
     */
    private static void line(StringBuilder text, String label, String value, String note) {
        text.append(String.format(Locale.ROOT, "  %-38s %10s", label, value));
        if (!note.isEmpty()) {
            text.append("   ").append(note);
        }
        text.append(System.lineSeparator());
    }

    /**
     * Formats a fraction as a percentage with two decimals, or "n/a" when it is not available.
     */
    static String percent(double fraction) {
        return Double.isNaN(fraction) ? "n/a" : String.format(Locale.ROOT, "%.2f %%", fraction * 100);
    }

    /**
     * Formats a number for the CSV file, leaving the field empty when it is not available.
     */
    private static String number(double value) {
        return Double.isNaN(value) ? "" : String.format(Locale.ROOT, "%.4f", value);
    }

    /**
     * Quotes a text field for the CSV file when it contains a comma, a quote or a line break.
     */
    private static String csv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
