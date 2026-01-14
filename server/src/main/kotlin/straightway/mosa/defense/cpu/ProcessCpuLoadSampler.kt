package straightway.mosa.defense.cpu

import java.lang.management.ManagementFactory

class ProcessCpuLoadSampler : CpuLoadSampler {
    private val processBean = ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean
    override val isOperational: Boolean get() = sample().let { it.isFinite() && 0.0 <= it }

    override fun toString() = "ProcessCpuLoadSampler"

    override fun sample(): Double = processBean?.processCpuLoad ?: Double.NaN
}
