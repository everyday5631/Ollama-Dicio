package org.stypox.dicio.llm

import android.util.Log
import java.io.File

/**
 * Works out how many threads llama.cpp should use on this device.
 *
 * This matters more on a phone than on a desktop. Android SoCs are big.LITTLE: a Tensor G4, for
 * instance, is one Cortex-X4 plus three A720s plus **four A520 efficiency cores**. llama.cpp splits
 * each layer evenly across its threads and synchronises at the end of it, so every step runs at the
 * pace of the *slowest* thread. Handing it a little core does not add throughput — it holds
 * everything else up, and using all eight cores is measurably worse than using the four fast ones.
 *
 * `Runtime.availableProcessors()` counts every core and cannot tell them apart, so the clusters are
 * identified by their maximum clock, which the kernel exposes per core.
 */
object CpuTopology {

    private val TAG = CpuTopology::class.simpleName

    /**
     * Cores whose peak clock is at least this fraction of the fastest core's are treated as
     * "performance" cores.
     *
     * 0.75 is chosen to span a prime+performance cluster while excluding the efficiency one. On a
     * Tensor G4 (X4 ≈ 3.1 GHz, A720 ≈ 2.6 GHz, A520 ≈ 1.9 GHz) the cut lands at ~2.3 GHz, which
     * keeps the X4 and the A720s and drops the A520s — four threads, which is what we want.
     */
    private const val PERFORMANCE_RATIO = 0.75

    /** Never use fewer than this, even if detection produces something silly. */
    private const val MIN_THREADS = 2

    /**
     * Beyond this, the per-layer synchronisation costs more than the extra parallelism returns for
     * models of the size that fit on a phone.
     */
    private const val MAX_THREADS = 6

    /** Cached, because this reads a handful of files and the answer cannot change at runtime. */
    val inferenceThreads: Int by lazy { detectPerformanceCores() }

    private fun detectPerformanceCores(): Int {
        val total = Runtime.getRuntime().availableProcessors()
        val threads = selectThreadCount(readMaxFrequencies(total), total)
        Log.i(TAG, "Using $threads inference thread(s) of $total core(s)")
        return threads
    }

    /**
     * The pure part of the decision, split out so it can be tested without a device.
     *
     * @param frequencies peak clock per core in kHz, empty if the kernel would not report them
     * @param totalCores what [Runtime.availableProcessors] reported
     */
    internal fun selectThreadCount(frequencies: List<Long>, totalCores: Int): Int {
        if (frequencies.isEmpty()) {
            // cpufreq unreadable (some devices restrict it): half the cores is a decent guess,
            // since big.LITTLE designs rarely give the fast cluster more than half
            return (totalCores / 2).coerceIn(MIN_THREADS, MAX_THREADS)
        }
        val cutoff = frequencies.max() * PERFORMANCE_RATIO
        return frequencies.count { it >= cutoff }.coerceIn(MIN_THREADS, MAX_THREADS)
    }

    /** Peak clock in kHz for each core, skipping any the kernel will not report. */
    private fun readMaxFrequencies(cpuCount: Int): List<Long> = buildList {
        for (cpu in 0 until cpuCount) {
            val value = runCatching {
                File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq")
                    .readText()
                    .trim()
                    .toLong()
            }.getOrNull()
            if (value != null && value > 0) {
                add(value)
            }
        }
    }
}
