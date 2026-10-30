/*
 * RISU-PPUM: SMRF, SLRF and SDIF for hiding sensitive high-utility itemsets.
 * Copyright (C) 2023-2026 Mohamed Ashraf
 * SPDX-License-Identifier: GPL-3.0-only
 */
package eg.edu.asu.ppum.evaluation;

/**
 * Measures the peak heap memory in use while an algorithm runs. Before the run it asks the garbage collector to
 * clean up, then a background thread samples the heap in use every few milliseconds until the run ends. The
 * result is the largest amount of heap memory in use during the run, the same quantity that SPMF reports as
 * maximum memory, so the numbers are comparable with those of high-utility itemset mining papers.
 */
public final class MemoryMonitor {

    private static final long SAMPLING_INTERVAL_MILLIS = 5;

    private final Thread sampler;
    private volatile boolean running = true;
    private volatile long peakBytes;

    /**
     * Cleans the heap, records the memory in use and starts the sampling thread.
     */
    private MemoryMonitor() {
        System.gc();
        peakBytes = usedBytes();
        sampler = new Thread(this::sampleUntilStopped, "memory-monitor");
        sampler.setDaemon(true);
        sampler.start();
    }

    /**
     * Starts measuring.
     */
    public static MemoryMonitor start() {
        return new MemoryMonitor();
    }

    /**
     * Stops measuring and returns the peak heap memory in use since the start, in megabytes.
     */
    public double stopAndGetPeakMegabytes() {
        running = false;
        try {
            sampler.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        record();
        return peakBytes / (1024.0 * 1024.0);
    }

    /**
     * Samples the heap in use until the measurement stops.
     */
    private void sampleUntilStopped() {
        while (running) {
            record();
            try {
                Thread.sleep(SAMPLING_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /**
     * Keeps the current heap usage if it is the largest seen so far.
     */
    private void record() {
        long used = usedBytes();
        if (used > peakBytes) {
            peakBytes = used;
        }
    }

    /**
     * Returns the heap memory currently in use, in bytes.
     */
    private static long usedBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
