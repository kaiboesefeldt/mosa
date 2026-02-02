package straightway.mosa.defense.cpu

import straightway.mosa.NanoClock
import java.util.concurrent.atomic.AtomicReference

class ProcessCpuTimeSampler(
    private val nanoClock: NanoClock,
    private val minSampleIntervalNs: Long,
    private val numberOfProcessors: Int,
    private val osBean: com.sun.management.OperatingSystemMXBean,
) : CpuLoadSampler {
    private val state =
        AtomicReference(
            State(
                nanoClock.nanoTime(),
                osBean.processCpuTime,
                Double.NaN,
            ),
        )

    init {
        require(0 < minSampleIntervalNs) { "minSampleIntervalNs must be positive" }
    }

    override val isAvailable: Boolean = true
    override val isOperational: Boolean
        get() = 0 <= osBean.processCpuTime

    override fun toString() = "ProcessCpuTimeSampler"

    override fun sample(): Double {
        val cpuTime = osBean.processCpuTime
        if (cpuTime < 0L) return Double.NaN
        val nowNs = nanoClock.nanoTime()
        return state
            .updateAndGet { previous ->
                when {
                    nowNs <= previous.lastSampleNs || cpuTime <= previous.lastCpuTimeNs ->
                        previous
                    nowNs - previous.lastSampleNs < minSampleIntervalNs ->
                        previous
                    else -> {
                        val elapsed = nowNs - previous.lastSampleNs
                        val cpuElapsed = cpuTime - previous.lastCpuTimeNs
                        val load = (cpuElapsed.toDouble() / elapsed.toDouble()) / numberOfProcessors
                        State(nowNs, cpuTime, load.coerceAtLeast(0.0))
                    }
                }
            }.lastValue
    }

    private data class State(
        val lastSampleNs: Long,
        val lastCpuTimeNs: Long,
        val lastValue: Double,
    )
}
