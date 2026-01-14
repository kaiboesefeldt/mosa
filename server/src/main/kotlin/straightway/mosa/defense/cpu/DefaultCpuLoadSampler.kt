package straightway.mosa.defense.cpu

fun defaultCpuLoadSampler(
    oshiSampler: CpuLoadSampler = OshiCpuLoadSampler(),
    processSampler: CpuLoadSampler = ProcessCpuLoadSampler(),
    systemLoadAverageSampler: CpuLoadSampler = SystemLoadAverageCpuLoadSampler(),
    processorCount: Int = Runtime.getRuntime().availableProcessors(),
): CpuLoadSampler =
    FirstOperationalCpuLoadSampler(
        oshiSampler,
        processSampler,
        PerCoreCpuLoadSampler(systemLoadAverageSampler, processorCount),
    )
