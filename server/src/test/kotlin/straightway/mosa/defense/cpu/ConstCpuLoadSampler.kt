package straightway.mosa.defense.cpu

data class ConstCpuLoadSampler(
    val fixedSample: Double,
    override val isAvailable: Boolean = true,
    override val isOperational: Boolean = true,
) : CpuLoadSampler {
    override fun sample() = fixedSample
}
