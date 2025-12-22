package straightway.mosa.defense

import java.lang.management.ManagementFactory
import kotlin.math.max

class CpuSheddingDecider(
    private val loadSheddingThreshold: Double,
    private val loadSampler: () -> Double = CpuLoadGauge()::sample,
) : SheddingDecider {
    override fun shouldShed() =
        loadSampler().coerceAtLeast(0.0).let { load ->
            if (loadSheddingThreshold <= load) {
                SheddingDecider.Result(
                    sheddingFactor = 1.0,
                    reason = "CPU load per core above $loadSheddingThreshold ($load)",
                )
            } else {
                SheddingDecider.Result.NO_SHEDDING
            }
        }
}

class CpuLoadGauge(
    private val systemLoadAverageProvider: () -> Double =
        { ManagementFactory.getOperatingSystemMXBean().systemLoadAverage },
    private val processorCount: Int = Runtime.getRuntime().availableProcessors(),
) {
    fun sample(): Double = normalizedCpuLoad()

    private fun normalizedCpuLoad(): Double {
        val loadAverage = systemLoadAverageProvider()
        if (loadAverage < 0.0) return 0.0
        return loadAverage / max(processorCount, 1)
    }
}
