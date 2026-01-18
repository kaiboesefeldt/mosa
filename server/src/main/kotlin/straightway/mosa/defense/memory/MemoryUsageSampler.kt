package straightway.mosa.defense.memory

interface MemoryUsageSampler {
    val isAvailable: Boolean

    fun sample(): Double
}
