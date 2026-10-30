/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.algorithms;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Creates sanitization algorithms from their names. SMRF, SLRF and SDIF are built in. Any other name is taken as
 * the fully qualified name of a class that implements {@link SanitizationAlgorithm} and has a public constructor
 * without parameters, so a new algorithm can be run without changing the framework: put its class on the class
 * path and give its name.
 */
public final class Algorithms {

    private Algorithms() {
    }

    /**
     * Returns the names of the built-in algorithms: SMRF, SLRF and SDIF.
     */
    public static List<String> builtIn() {
        return Collections.unmodifiableList(Arrays.asList("SMRF", "SLRF", "SDIF"));
    }

    /**
     * Creates the algorithm with the given name: SMRF, SLRF or SDIF in any letter case, or the full class name
     * of another algorithm, for example "com.example.MyAlgorithm".
     */
    public static SanitizationAlgorithm create(String name) {
        String trimmed = name.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.equals("SMRF")) {
            return new SMRF();
        }
        if (upper.equals("SLRF")) {
            return new SLRF();
        }
        if (upper.equals("SDIF")) {
            return new SDIF();
        }
        Class<?> type;
        try {
            type = Class.forName(trimmed);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            throw new IllegalArgumentException("unknown algorithm '" + trimmed + "': use SMRF, SLRF, SDIF, or the full"
                    + " class name of your own algorithm, with its class on the class path");
        }
        if (!SanitizationAlgorithm.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException(trimmed + " does not implement " + SanitizationAlgorithm.class.getName());
        }
        try {
            return (SanitizationAlgorithm) type.getConstructor().newInstance();
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("the constructor of " + trimmed + " failed: " + e.getCause(), e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("cannot create " + trimmed
                    + ": it must be a public, non-abstract class with a public constructor without parameters", e);
        }
    }
}
