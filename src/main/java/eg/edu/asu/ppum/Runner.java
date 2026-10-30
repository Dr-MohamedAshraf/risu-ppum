/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum;

import eg.edu.asu.ppum.algorithms.Algorithms;
import eg.edu.asu.ppum.algorithms.SanitizationAlgorithm;
import eg.edu.asu.ppum.data.DataFormatException;
import eg.edu.asu.ppum.data.Itemset;
import eg.edu.asu.ppum.data.ItemsetFile;
import eg.edu.asu.ppum.data.TransactionDatabase;
import eg.edu.asu.ppum.data.UtilityTable;
import eg.edu.asu.ppum.evaluation.RunResult;
import eg.edu.asu.ppum.evaluation.SideEffects;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The entry point of the framework, from an IDE or the command line. It loads an experiment, takes the
 * sensitive itemsets from a file or draws them at random, runs every selected algorithm on its own copy of the
 * database, prints a report after each algorithm and a comparison table at the end, and writes into the output
 * folder:
 * <ul>
 *   <li>{@code sensitive_itemsets.txt}: the sensitive itemsets that were hidden;</li>
 *   <li>{@code ALGORITHM/sanitized.txt}: the sanitized database of each algorithm, in the input format;</li>
 *   <li>{@code ALGORITHM/report.txt}: the report printed for that algorithm;</li>
 *   <li>{@code results.csv}: one row per run, appended, to compare algorithms and experiments.</li>
 * </ul>
 * To run from an IDE, edit {@link #ideSettings()} and run this class. On the command line, the options listed by
 * {@code --help} replace those settings.
 */
public final class Runner {

    public static final String VERSION = "1.0.0";

    private static final String USAGE = String.join(System.lineSeparator(),
            "Usage: java -jar risu-ppum-" + VERSION + ".jar [options]",
            "Without options, the settings written in Runner.ideSettings() are used.",
            "",
            "  --db FILE            transactions, one per line as item:quantity pairs",
            "  --profits FILE       unit profits, one 'item, profit' pair per line",
            "  --huis FILE          high-utility itemsets of the database in SPMF format, mined with any algorithm",
            "  --min-util VALUE     minimum utility: a percentage of the total utility (21%) or a number (70)",
            "  --shis FILE          sensitive itemsets to hide, in SPMF format (lines copied from the HUI file)",
            "  --random-shis N      instead of --shis, draw N high-utility itemsets with at least two items",
            "  --seed S             seed of the random draw (default 1)",
            "  --algorithms LIST    comma-separated names: SMRF, SLRF, SDIF, or full class names of other",
            "                       algorithms on the class path (default SMRF,SLRF,SDIF)",
            "  --out FOLDER         output folder (default results)",
            "  --export-spmf FILE   only write the database in SPMF format, to mine it with an HUIM algorithm",
            "  --help               show this help");

    private Runner() {
    }

    /**
     * The settings of a run; paths are relative to the working directory.
     * {@code database}, {@code profits} and {@code highUtilityItemsets} are the three input files and
     * {@code minUtil} the threshold, such as "21%" or "70". The sensitive itemsets come either from the file
     * {@code sensitiveItemsets} or, when that is null, from a random draw of {@code randomSensitiveItemsets}
     * itemsets with {@code seed}. {@code algorithms} lists the algorithms to run, {@code outputFolder} receives
     * the results, and {@code exportSpmf}, when set, only converts the database to SPMF format.
     */
    public static final class Settings {
        public String database;
        public String profits;
        public String highUtilityItemsets;
        public String minUtil;
        public String sensitiveItemsets;
        public int randomSensitiveItemsets;
        public long seed = 1;
        public List<String> algorithms = new ArrayList<String>(Algorithms.builtIn());
        public String outputFolder = "results";
        public String exportSpmf;
    }

    /**
     * The settings used when the program starts without command-line options. Edit these values to run the
     * framework from an IDE. As delivered, they run SMRF, SLRF and SDIF on the worked example of the paper: the
     * database of Tables 2 and 3 with items A to F written as 1 to 6, minUtil 70, and the three sensitive itemsets
     * of Section 5.
     */
    public static Settings ideSettings() {
        Settings settings = new Settings();
        settings.database = "examples/paper-example/transactions.txt";
        settings.profits = "examples/paper-example/profits.txt";
        settings.highUtilityItemsets = "examples/paper-example/huis.txt";
        settings.minUtil = "70";
        settings.sensitiveItemsets = "examples/paper-example/shis.txt";
        settings.randomSensitiveItemsets = 0;
        settings.seed = 1;
        settings.algorithms = Arrays.asList("SMRF", "SLRF", "SDIF");
        settings.outputFolder = "results/paper-example";
        return settings;
    }

    /**
     * Starts the program with the IDE settings, or with the command-line options when there are any. Input
     * problems are reported in one line, without a stack trace.
     */
    public static void main(String[] args) {
        try {
            if (Arrays.asList(args).contains("--help") || Arrays.asList(args).contains("-h")) {
                System.out.println(USAGE);
                return;
            }
            run(args.length == 0 ? ideSettings() : parse(args), System.out);
        } catch (DataFormatException | IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (NoSuchFileException e) {
            System.err.println("Error: file not found: " + e.getFile());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error: " + e);
            System.exit(1);
        }
    }

    /**
     * Runs an experiment with the given settings, prints the reports to {@code out} and writes the result files.
     * Only one sanitized database is kept in memory at a time, so the memory measured for each algorithm does
     * not include the results of the algorithms run before it.
     */
    public static void run(Settings settings, PrintStream out) throws IOException {
        require(settings.database, "--db");
        require(settings.profits, "--profits");
        if (settings.exportSpmf != null) {
            exportSpmf(settings, out);
            return;
        }
        require(settings.highUtilityItemsets, "--huis");
        require(settings.minUtil, "--min-util");
        boolean fromFile = settings.sensitiveItemsets != null;
        if (fromFile == (settings.randomSensitiveItemsets > 0)) {
            throw new IllegalArgumentException("give exactly one of --shis FILE (sensitive itemsets to hide) or"
                    + " --random-shis N (a positive number of itemsets to draw)");
        }
        if (settings.algorithms == null || settings.algorithms.isEmpty()) {
            throw new IllegalArgumentException("no algorithm selected (--algorithms)");
        }
        List<SanitizationAlgorithm> algorithms = new ArrayList<SanitizationAlgorithm>();
        Set<String> folders = new HashSet<String>();
        for (String name : settings.algorithms) {
            SanitizationAlgorithm algorithm = Algorithms.create(name);
            if (algorithm.name() == null || algorithm.name().trim().isEmpty()) {
                throw new IllegalArgumentException(name + ": name() must return a short, non-empty name");
            }
            if (!folders.add(folderName(algorithm.name()).toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("algorithm " + algorithm.name() + " is selected twice");
            }
            algorithms.add(algorithm);
        }

        Experiment experiment = Experiment.load(Paths.get(settings.database), Paths.get(settings.profits),
                Paths.get(settings.highUtilityItemsets), settings.minUtil);
        List<Itemset> sensitive = fromFile
                ? experiment.readSensitiveItemsets(Paths.get(settings.sensitiveItemsets))
                : experiment.drawSensitiveItemsets(settings.randomSensitiveItemsets, settings.seed);
        String source = fromFile ? settings.sensitiveItemsets : "random, seed " + settings.seed;
        Path output = Paths.get(settings.outputFolder);
        Files.createDirectories(output);
        ItemsetFile.write(output.resolve("sensitive_itemsets.txt"), sensitive);
        printInputs(experiment, sensitive, source, algorithms, output, out);

        Path csv = output.resolve("results.csv");
        int nameWidth = "Algorithm".length();
        for (SanitizationAlgorithm algorithm : algorithms) {
            nameWidth = Math.max(nameWidth, algorithm.name().length());
        }
        List<String> comparison = new ArrayList<String>();
        for (SanitizationAlgorithm algorithm : algorithms) {
            RunResult result = experiment.run(algorithm, sensitive);
            Path folder = output.resolve(folderName(result.algorithm()));
            Files.createDirectories(folder);
            Path sanitizedFile = folder.resolve("sanitized.txt");
            result.sanitizedDatabase().write(sanitizedFile);
            String report = result.report(sanitizedFile.toString());
            Files.write(folder.resolve("report.txt"), report.getBytes(StandardCharsets.UTF_8));
            appendCsv(csv, result.csvRow(experiment.databaseName(), experiment.minUtil(), source));
            comparison.add(comparisonRow(result, nameWidth));
            out.println();
            out.print(report);
        }
        if (comparison.size() > 1) {
            out.println();
            out.println("Comparison");
            out.println(comparisonHeader(nameWidth));
            for (String row : comparison) {
                out.println(row);
            }
        }
        out.println();
        out.println("Results appended to " + csv);
    }

    /**
     * Reads command-line options into settings. Every option takes one value.
     */
    static Settings parse(String[] args) {
        Settings settings = new Settings();
        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            if (!option.startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument '" + option + "'; run with --help");
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("missing value after " + option);
            }
            String value = args[++i];
            switch (option) {
                case "--db":
                    settings.database = value;
                    break;
                case "--profits":
                    settings.profits = value;
                    break;
                case "--huis":
                    settings.highUtilityItemsets = value;
                    break;
                case "--min-util":
                    settings.minUtil = value;
                    break;
                case "--shis":
                    settings.sensitiveItemsets = value;
                    break;
                case "--random-shis":
                    settings.randomSensitiveItemsets = (int) Math.min(Integer.MAX_VALUE, number(value, option));
                    break;
                case "--seed":
                    settings.seed = number(value, option);
                    break;
                case "--algorithm":
                case "--algorithms":
                    settings.algorithms = names(value);
                    break;
                case "--out":
                    settings.outputFolder = value;
                    break;
                case "--export-spmf":
                    settings.exportSpmf = value;
                    break;
                default:
                    throw new IllegalArgumentException("unknown option " + option + "; run with --help");
            }
        }
        return settings;
    }

    /**
     * Writes the database in SPMF format so that its high-utility itemsets can be mined with any SPMF-compatible
     * miner, and prints the absolute threshold to give the miner when a minimum utility is set.
     */
    private static void exportSpmf(Settings settings, PrintStream out) throws IOException {
        UtilityTable profits = UtilityTable.read(Paths.get(settings.profits));
        TransactionDatabase database = TransactionDatabase.read(Paths.get(settings.database), profits);
        Path target = Paths.get(settings.exportSpmf);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        database.writeSpmfFormat(target, profits);
        long totalUtility = database.totalUtility(profits);
        out.println("Wrote " + target + " in SPMF format: " + database.size() + " transactions, total utility "
                + totalUtility);
        if (settings.minUtil != null) {
            out.println("minUtil " + settings.minUtil + " = " + Experiment.parseMinUtil(settings.minUtil, totalUtility)
                    + " (the absolute value to give the miner)");
        }
    }

    /**
     * Prints a summary of the inputs and of any notes raised while checking them.
     */
    private static void printInputs(Experiment experiment, List<Itemset> sensitive, String source,
                                    List<SanitizationAlgorithm> algorithms, Path output, PrintStream out) {
        Set<Itemset> sensitiveSet = new HashSet<Itemset>(sensitive);
        int nonSensitive = 0;
        for (Itemset itemset : experiment.highUtilityItemsets()) {
            if (!sensitiveSet.contains(itemset)) {
                nonSensitive++;
            }
        }
        List<String> names = new ArrayList<String>();
        for (SanitizationAlgorithm algorithm : algorithms) {
            names.add(algorithm.name());
        }
        out.println("RISU-PPUM " + VERSION);
        out.printf(Locale.ROOT, "%-22s %s: %d transactions, total utility %d%n", "Database",
                experiment.databaseName(), experiment.database().size(), experiment.totalUtility());
        out.printf(Locale.ROOT, "%-22s %d%n", "Minimum utility", experiment.minUtil());
        out.printf(Locale.ROOT, "%-22s %d%n", "High-utility itemsets", experiment.highUtilityItemsets().size());
        out.printf(Locale.ROOT, "%-22s %d (%s); %d non-sensitive high-utility itemsets%n", "Sensitive itemsets",
                sensitive.size(), source, nonSensitive);
        out.printf(Locale.ROOT, "%-22s %s%n", "Algorithms", String.join(", ", names));
        out.printf(Locale.ROOT, "%-22s %s%n", "Output folder", output);
        for (String note : experiment.takeNotes()) {
            out.println("Note: " + note);
        }
    }

    /**
     * Returns the header of the comparison table printed after all algorithms have run; the first column is
     * as wide as the longest algorithm name.
     */
    private static String comparisonHeader(int nameWidth) {
        return String.format(Locale.ROOT, "  %-" + nameWidth + "s %8s %8s %8s %8s %8s %8s %12s %12s", "Algorithm",
                "HF %", "AC %", "MC %", "IUS %", "DUS %", "TMR %", "Time (ms)", "Memory (MB)");
    }

    /**
     * Returns the line of the comparison table for one run.
     */
    private static String comparisonRow(RunResult result, int nameWidth) {
        SideEffects effects = result.sideEffects();
        return String.format(Locale.ROOT, "  %-" + nameWidth + "s %8s %8s %8s %8s %8s %8s %12.2f %12.2f",
                result.algorithm(),
                plainPercent(effects.hidingFailure()), plainPercent(effects.artificialCost()),
                plainPercent(effects.missingCost()), plainPercent(effects.itemsetUtilitySimilarity()),
                plainPercent(effects.datasetUtilitySimilarity()), plainPercent(effects.transactionModificationRatio()),
                result.runtimeMillis(), result.peakMemoryMegabytes());
    }

    /**
     * Formats a fraction as a percentage number with two decimals, or "n/a" when it is not available.
     */
    private static String plainPercent(double fraction) {
        return Double.isNaN(fraction) ? "n/a" : String.format(Locale.ROOT, "%.2f", fraction * 100);
    }

    /**
     * Appends a row to the CSV file of results, writing the header first when the file is new.
     */
    private static void appendCsv(Path csv, String row) throws IOException {
        StringBuilder text = new StringBuilder();
        if (!Files.exists(csv) || Files.size(csv) == 0) {
            text.append(RunResult.csvHeader()).append(System.lineSeparator());
        }
        text.append(row).append(System.lineSeparator());
        Files.write(csv, text.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    /**
     * Turns an algorithm name into a safe folder name.
     */
    private static String folderName(String algorithmName) {
        return algorithmName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /**
     * Splits a comma-separated list of algorithm names.
     */
    private static List<String> names(String value) {
        List<String> names = new ArrayList<String>();
        for (String name : value.split(",")) {
            if (!name.trim().isEmpty()) {
                names.add(name.trim());
            }
        }
        return names;
    }

    /**
     * Parses the whole-number value of a command-line option.
     */
    private static long number(String value, String option) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " needs a whole number, found '" + value + "'");
        }
    }

    /**
     * Fails with a clear message when a required setting is missing.
     */
    private static void require(String value, String option) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("missing required option " + option + "; run with --help");
        }
    }
}
