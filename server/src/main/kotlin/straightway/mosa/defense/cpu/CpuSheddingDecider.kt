package straightway.mosa.defense.cpu

import straightway.mosa.defense.core.SheddingDecider
import straightway.mosa.defense.core.SheddingRequest

class CpuSheddingDecider(
    private val config: CpuSheddingConfig,
    private val loadSampler: CpuLoadSampler = PerCoreCpuLoadSampler(SystemLoadAverageCpuLoadSampler()),
) : SheddingDecider {
    override suspend fun decideShedding(request: SheddingRequest): SheddingDecider.Result =
        with(loadSampler) {
            if (!isOperational) return SheddingDecider.Result.NO_SHEDDING
            sample().let { load ->
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
        }

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
