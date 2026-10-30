/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The unit profit (external utility) of every item. Multiplied by the quantity of an item in a transaction, it
 * gives the utility of the item in that transaction (Definition 1 of the paper).
 */
public final class UtilityTable {

    private final Map<Integer, Integer> unitProfits;

    /**
     * Creates a table from item-to-profit pairs. Items must be non-negative and profits positive, as in the
     * paper.
     */
    public UtilityTable(Map<Integer, Integer> unitProfits) {
        for (Map.Entry<Integer, Integer> entry : unitProfits.entrySet()) {
            if (entry.getKey() < 0 || entry.getValue() <= 0) {
                throw new IllegalArgumentException(
                        "invalid unit profit " + entry.getValue() + " for item " + entry.getKey());
            }
        }
        this.unitProfits = new HashMap<Integer, Integer>(unitProfits);
    }

    /**
     * Reads a profit file with one "item, profit" pair per line, the format of the original code. Blank lines
     * are ignored. A malformed line, an item listed twice or a profit that is not a positive integer stops the
     * reading with a message naming the line.
     */
    public static UtilityTable read(Path file) throws IOException {
        String name = file.toString();
        Map<Integer, Integer> profits = new HashMap<Integer, Integer>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                String text = line.trim();
                if (text.isEmpty()) {
                    continue;
                }
                int comma = text.indexOf(',');
                if (comma < 0) {
                    throw DataFormatException.at(name, number, "expected 'item, profit' but found '" + text + "'");
                }
                int item = Numbers.item(text.substring(0, comma).trim(), name, number);
                int profit = Numbers.positive(text.substring(comma + 1).trim(), "unit profit", name, number);
                if (profits.put(item, profit) != null) {
                    throw DataFormatException.at(name, number, "item " + item + " is listed twice");
                }
            }
        }
        if (profits.isEmpty()) {
            throw new DataFormatException(name + ": the profit file is empty");
        }
        return new UtilityTable(profits);
    }

    /**
     * Returns the unit profit of an item. It fails when the item has no profit, which means that the database
     * and the profit file do not belong together.
     */
    public int unitProfit(int item) {
        Integer profit = unitProfits.get(item);
        if (profit == null) {
            throw new IllegalArgumentException("item " + item + " has no unit profit");
        }
        return profit;
    }

    /**
     * Tells whether the item has a unit profit.
     */
    public boolean contains(int item) {
        return unitProfits.containsKey(item);
    }
}
