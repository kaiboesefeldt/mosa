package straightway.mosa.defense.cpu

interface CpuLoadSampler {
    val isAvailable: Boolean
    val isOperational: Boolean

    fun sample(): Double
}
