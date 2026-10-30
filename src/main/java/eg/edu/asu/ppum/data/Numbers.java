/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.data;

/**
 * Parses the numbers found in the input files. When a value is not valid, it reports the file, the line and
 * the kind of value that was expected, instead of a bare number-format error.
 */
final class Numbers {

    private Numbers() {
    }

    /**
     * Parses an item identifier, which must be a non-negative integer.
     */
    static int item(String text, String file, int line) {
        int value = integer(text, "item", file, line);
        if (value < 0) {
            throw DataFormatException.at(file, line, "an item must be a non-negative integer, found '" + text + "'");
        }
        return value;
    }

    /**
     * Parses a positive integer such as a quantity or a unit profit; the name of the value is used in messages.
     */
    static int positive(String text, String name, String file, int line) {
        int value = integer(text, name, file, line);
        if (value <= 0) {
            throw DataFormatException.at(file, line, "a " + name + " must be a positive integer, found '" + text + "'");
        }
        return value;
    }

    /**
     * Parses the utility of an itemset, a non-negative whole number that may exceed the range of an int.
     */
    static long utility(String text, String file, int line) {
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw DataFormatException.at(file, line, "a utility must be a whole number, found '" + text + "'");
        }
        if (value < 0) {
            throw DataFormatException.at(file, line, "a utility cannot be negative, found '" + text + "'");
        }
        return value;
    }

    /**
     * Parses an integer and reports a format error that names the expected value.
     */
    private static int integer(String text, String name, String file, int line) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw DataFormatException.at(file, line, "a " + name + " must be an integer, found '" + text + "'");
        }
    }
}
