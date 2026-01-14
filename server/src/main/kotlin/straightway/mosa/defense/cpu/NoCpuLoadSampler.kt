package straightway.mosa.defense.cpu

class NoCpuLoadSampler : CpuLoadSampler {
    override val isOperational = false

    override fun toString() = "NoCpuLoadSampler"

    override fun sample() = Double.NaN
}
