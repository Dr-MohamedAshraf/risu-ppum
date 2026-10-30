/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.data;

/**
 * Signals that an input file does not follow the expected format, or that the input files do not belong
 * together. The message names the file and, when possible, the line, so the problem can be fixed quickly.
 */
public class DataFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a complete message.
     */
    public DataFormatException(String message) {
        super(message);
    }

    /**
     * Creates the exception for a problem on one line of a file, with the message "file:line: problem".
     */
    static DataFormatException at(String file, int line, String problem) {
        return new DataFormatException(file + ":" + line + ": " + problem);
    }
}
