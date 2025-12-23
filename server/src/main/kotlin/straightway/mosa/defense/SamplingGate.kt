package straightway.mosa.defense

class SamplingGate(
    @Volatile
    var lastSampleNs: Long = 0L,
) {
    fun isWithinSamplingInterval(
        nowNs: Long,
        sampleIntervalNs: Long,
    ): Boolean = nowNs - lastSampleNs < sampleIntervalNs
}
