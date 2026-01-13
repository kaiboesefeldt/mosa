package straightway.mosa.defense.cpu

class ConstCpuLoadSampler(
    val fixedSample: Double,
    override val isOperational: Boolean = true,
) : CpuLoadSampler {
    override fun sample() = fixedSample
}
