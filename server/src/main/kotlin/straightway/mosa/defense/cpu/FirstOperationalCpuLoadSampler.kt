package straightway.mosa.defense.cpu

class FirstOperationalCpuLoadSampler(
    candidates: List<CpuLoadSampler>,
) : CpuLoadSampler by (candidates.firstOrNull { it.isOperational } ?: NoCpuLoadSampler()) {
    companion object {
        operator fun invoke(vararg candidates: CpuLoadSampler) = FirstOperationalCpuLoadSampler(candidates.toList())
    }
}
