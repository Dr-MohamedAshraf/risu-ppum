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
import java.util.List;

/**
 * Reads and writes itemset files in the SPMF format written by high-utility itemset miners: one itemset per
 * line, its items separated by spaces, followed by "#UTIL:" and its utility, for example "1 2 5 #UTIL: 82".
 */
public final class ItemsetFile {

    private ItemsetFile() {
    }

    /**
     * Reads an itemset file. Blank lines are skipped, and other fields that some miners add after the items,
     * such as "#SUP: 4", are ignored. A line without items or without a utility stops the reading.
     */
    public static List<Itemset> read(Path file) throws IOException {
        String name = file.toString();
        List<Itemset> itemsets = new ArrayList<Itemset>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                String text = line.trim();
                if (!text.isEmpty()) {
                    itemsets.add(parse(text, name, number));
                }
            }
        }
        return itemsets;
    }

    /**
     * Writes itemsets in SPMF format, one per line, in the given order.
     */
    public static void write(Path file, List<Itemset> itemsets) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (Itemset itemset : itemsets) {
                writer.write(itemset.toString());
                writer.newLine();
            }
        }
    }

    /**
     * Parses one non-empty line of an itemset file.
     */
    private static Itemset parse(String text, String file, int number) {
        String[] tokens = text.split("\\s+");
        List<Integer> items = new ArrayList<Integer>();
        int index = 0;
        while (index < tokens.length && !tokens[index].startsWith("#")) {
            items.add(Numbers.item(tokens[index], file, number));
            index++;
        }
        if (items.isEmpty()) {
            throw DataFormatException.at(file, number, "no items before '#UTIL:'");
        }
        Long utility = null;
        for (int i = index; i < tokens.length && utility == null; i++) {
            if (tokens[i].equals("#UTIL:") && i + 1 < tokens.length) {
                utility = Numbers.utility(tokens[i + 1], file, number);
            } else if (tokens[i].startsWith("#UTIL:") && tokens[i].length() > "#UTIL:".length()) {
                utility = Numbers.utility(tokens[i].substring("#UTIL:".length()), file, number);
            }
        }
        if (utility == null) {
            throw DataFormatException.at(file, number, "missing '#UTIL: utility' after the items");
        }
        int[] array = new int[items.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = items.get(i);
        }
        try {
            return new Itemset(array, utility);
        } catch (IllegalArgumentException e) {
            throw DataFormatException.at(file, number, e.getMessage());
        }
    }
}
