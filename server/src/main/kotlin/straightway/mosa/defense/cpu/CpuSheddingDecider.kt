package straightway.mosa.defense.cpu

import straightway.mosa.defense.core.SheddingDecider
import straightway.mosa.defense.core.SheddingRequest
import java.lang.management.ManagementFactory
import kotlin.math.max

class CpuSheddingDecider(
    private val config: CpuSheddingConfig,
    private val loadSampler: () -> Double = CpuLoadGauge()::sample,
) : SheddingDecider {
    override suspend fun decideShedding(request: SheddingRequest) =
        normalizedLoad().let { load ->
            val sheddingFactor = sheddingFactorFor(load)
            if (sheddingFactor <= 0.0) {
                SheddingDecider.Result.NO_SHEDDING
            } else {
                SheddingDecider.Result(
                    sheddingFactor = sheddingFactor,
                    reason = sheddingReason(load),
                )
            }
        }

    private fun normalizedLoad(): Double =
        loadSampler()
            .let { if (it.isFinite()) it else 0.0 }
            .coerceAtLeast(0.0)

    private fun sheddingFactorFor(load: Double): Double =
        when {
            load < config.loadSheddingStart -> 0.0
            config.loadSheddingFull <= load -> 1.0
            else ->
                ((load - config.loadSheddingStart) / (config.loadSheddingFull - config.loadSheddingStart))
                    .coerceIn(0.0, 1.0)
        }

    private fun sheddingReason(load: Double): String =
        if (config.loadSheddingFull <= load) {
            "CPU load per core above ${config.loadSheddingFull} ($load)"
        } else {
            "CPU load per core in shedding range ${config.loadSheddingStart}-${config.loadSheddingFull} ($load)"
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
