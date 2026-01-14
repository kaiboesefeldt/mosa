package straightway.mosa.defense.cpu

class PerCoreCpuLoadSampler(
    private val cpuLoadSampler: CpuLoadSampler,
    private val processorCount: Int = Runtime.getRuntime().availableProcessors(),
) : CpuLoadSampler {
    override val isOperational get() = cpuLoadSampler.isOperational && 0 < processorCount

    override fun toString() = "PerCoreCpuLoadSampler($cpuLoadSampler, processorCount=$processorCount)"

    override fun sample(): Double {
        val loadAverage = cpuLoadSampler.sample()
        return loadAverage / processorCount
    }
}
