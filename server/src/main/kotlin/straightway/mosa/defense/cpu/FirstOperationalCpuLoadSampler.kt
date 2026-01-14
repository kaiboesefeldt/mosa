package straightway.mosa.defense.cpu

class FirstOperationalCpuLoadSampler private constructor(
    private val delegate: CpuLoadSampler,
) : CpuLoadSampler by delegate {
    constructor(candidates: List<CpuLoadSampler>) :
        this(candidates.firstOrNull { it.isOperational } ?: NoCpuLoadSampler())

    override fun toString() = "FirstOperationalCpuLoadSampler($delegate)"

    companion object {
        operator fun invoke(vararg candidates: CpuLoadSampler) = FirstOperationalCpuLoadSampler(candidates.toList())
    }
}
