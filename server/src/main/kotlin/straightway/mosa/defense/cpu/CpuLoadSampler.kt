package straightway.mosa.defense.cpu

interface CpuLoadSampler {
    val isOperational: Boolean

    fun sample(): Double
}
