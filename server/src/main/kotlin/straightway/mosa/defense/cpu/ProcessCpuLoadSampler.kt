package straightway.mosa.defense.cpu

import java.lang.management.ManagementFactory

class ProcessCpuLoadSampler : CpuLoadSampler {
    private val processBean = ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean

    override val isAvailable: Boolean = processBean != null
    override val isOperational get() = sample().let { it.isFinite() && 0 <= it }

    override fun toString() = "ProcessCpuLoadSampler"

    override fun sample(): Double = processBean?.processCpuLoad ?: Double.NaN
}
