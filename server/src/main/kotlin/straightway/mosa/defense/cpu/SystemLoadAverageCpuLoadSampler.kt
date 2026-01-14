package straightway.mosa.defense.cpu

import java.lang.management.ManagementFactory

class SystemLoadAverageCpuLoadSampler : CpuLoadSampler {
    private val osBean = ManagementFactory.getOperatingSystemMXBean()

    override val isOperational: Boolean get() = sample().let { it.isFinite() && 0 <= it }

    override fun toString() = "SystemLoadAverageCpuLoadSampler"

    override fun sample(): Double = osBean.systemLoadAverage
}
