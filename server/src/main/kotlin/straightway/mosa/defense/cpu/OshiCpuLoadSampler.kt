package straightway.mosa.defense.cpu

import oshi.SystemInfo
import java.util.concurrent.atomic.AtomicReference

class OshiCpuLoadSampler : CpuLoadSampler {
    private val oshiHwProcessor = runCatching { SystemInfo().hardware.processor }.getOrNull()
    private val previousTicks = AtomicReference(initialTicks())

    override val isOperational = oshiHwProcessor != null

    override fun toString() = "OshiCpuLoadSampler"

    override fun sample(): Double =
        oshiHwProcessor?.run {
            runCatching {
                getSystemCpuLoadBetweenTicks(previousTicks.get()).also {
                    previousTicks.set(systemCpuLoadTicks)
                }
            }.getOrNull()
        } ?: Double.NaN

    private fun initialTicks(): LongArray =
        runCatching { oshiHwProcessor?.systemCpuLoadTicks }
            .getOrNull() ?: LongArray(0)
}
