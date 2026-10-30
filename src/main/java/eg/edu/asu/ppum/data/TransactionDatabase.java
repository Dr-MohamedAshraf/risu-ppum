/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.data;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * The quantitative transaction database: one transaction per line of the input file, kept in file order. The
 * position of a transaction (0, 1, 2, ...) is its transaction identifier (TID). Iterating over the database
 * visits the transactions in that order.
 */
public final class TransactionDatabase implements Iterable<Transaction> {

    private final Transaction[] transactions;

    /**
     * Creates a database from transactions; the array is used as given.
     */
    TransactionDatabase(Transaction[] transactions) {
        this.transactions = transactions;
    }

    /**
     * Reads a database file in the transaction format of the original code: every line is one transaction
     * written as "item:quantity" pairs separated by spaces, for example "1:5 2:4 3:2"; like the original code,
     * it also accepts "item,quantity". Items must be non-negative integers with a unit profit, quantities must
     * be positive integers, and an item may appear only once in a line. An empty line is read as an empty
     * transaction.
     */
    public static TransactionDatabase read(Path file, UtilityTable profits) throws IOException {
        String name = file.toString();
        List<Transaction> transactions = new ArrayList<Transaction>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                transactions.add(parse(line, profits, name, number));
            }
        }
        if (transactions.isEmpty()) {
            throw new DataFormatException(name + ": the database is empty");
        }
        return new TransactionDatabase(transactions.toArray(new Transaction[0]));
    }

    /**
     * Returns the number of transactions, |D|.
     */
    public int size() {
        return transactions.length;
    }

    /**
     * Returns the transaction with the given TID, from 0 to {@code size() - 1}.
     */
    public Transaction get(int tid) {
        return transactions[tid];
    }

    /**
     * Visits the transactions in TID order.
     */
    @Override
    public Iterator<Transaction> iterator() {
        return Collections.unmodifiableList(Arrays.asList(transactions)).iterator();
    }

    /**
     * Returns the total utility of the database: the sum of the utilities of all its transactions.
     */
    public long totalUtility(UtilityTable profits) {
        long utility = 0;
        for (Transaction transaction : transactions) {
            utility += transaction.transactionUtility(profits);
        }
        return utility;
    }

    /**
     * Returns an independent copy, so that an algorithm can sanitize it while the original stays unchanged.
     */
    public TransactionDatabase copy() {
        Transaction[] copies = new Transaction[transactions.length];
        for (int tid = 0; tid < transactions.length; tid++) {
            copies[tid] = transactions[tid].copy();
        }
        return new TransactionDatabase(copies);
    }

    /**
     * Writes the database in the input format, one transaction per line in TID order. A transaction that lost
     * all its items is written as an empty line, so line numbers still match the original file.
     */
    public void write(Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (Transaction transaction : transactions) {
                writer.write(transaction.toString());
                writer.newLine();
            }
        }
    }

    /**
     * Writes the database in the SPMF utility format read by most high-utility itemset miners: on each line the
     * items in increasing order, the transaction utility and the utility of each item, separated by colons, for
     * example "1 2 5:24:6 12 6". Empty transactions are left out because they cannot support any itemset. The
     * SPMF format numbers items from 1, so a database that contains item 0 is refused.
     */
    public void writeSpmfFormat(Path file, UtilityTable profits) throws IOException {
        for (Transaction transaction : transactions) {
            if (transaction.contains(0)) {
                throw new DataFormatException("item 0 cannot be written in SPMF format, whose items are positive"
                        + " integers; number the items from 1");
            }
        }
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (Transaction transaction : transactions) {
                if (transaction.isEmpty()) {
                    continue;
                }
                long[] packed = new long[transaction.size()];
                for (int position = 0; position < packed.length; position++) {
                    packed[position] = ((long) transaction.itemAt(position) << 32) | position;
                }
                Arrays.sort(packed);
                StringBuilder items = new StringBuilder();
                StringBuilder utilities = new StringBuilder();
                for (int k = 0; k < packed.length; k++) {
                    int item = (int) (packed[k] >>> 32);
                    int position = (int) packed[k];
                    if (k > 0) {
                        items.append(' ');
                        utilities.append(' ');
                    }
                    items.append(item);
                    utilities.append((long) transaction.quantityAt(position) * profits.unitProfit(item));
                }
                writer.write(items + ":" + transaction.transactionUtility(profits) + ":" + utilities);
                writer.newLine();
            }
        }
    }

    /**
     * Parses one line of a database file into a transaction.
     */
    private static Transaction parse(String line, UtilityTable profits, String file, int number) {
        String text = line.trim();
        if (text.isEmpty()) {
            return new Transaction(new int[0], new int[0]);
        }
        String[] tokens = text.split("\\s+");
        int[] items = new int[tokens.length];
        int[] quantities = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            int colon = tokens[i].indexOf(':');
            if (colon < 0) {
                colon = tokens[i].indexOf(',');
            }
            if (colon <= 0) {
                throw DataFormatException.at(file, number, "expected 'item:quantity' but found '" + tokens[i] + "'");
            }
            int item = Numbers.item(tokens[i].substring(0, colon), file, number);
            if (!profits.contains(item)) {
                throw DataFormatException.at(file, number, "item " + item + " has no unit profit in the profit file");
            }
            for (int j = 0; j < i; j++) {
                if (items[j] == item) {
                    throw DataFormatException.at(file, number, "item " + item + " appears twice in the transaction");
                }
            }
            items[i] = item;
            quantities[i] = Numbers.positive(tokens[i].substring(colon + 1), "quantity", file, number);
        }
        return new Transaction(items, quantities);
    }
}
