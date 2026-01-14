package straightway.mosa.defense.cpu

import straightway.mosa.NanoClock

fun defaultCpuLoadSampler(
    oshiSampler: CpuLoadSampler = OshiCpuLoadSampler(),
    processSampler: CpuLoadSampler = ProcessCpuLoadSampler(),
    systemLoadAverageSampler: CpuLoadSampler = SystemLoadAverageCpuLoadSampler(),
    processorCount: Int = Runtime.getRuntime().availableProcessors(),
    smoothingTimeNs: Long = 250_000_000L,
    nanoClock: NanoClock = NanoClock(System::nanoTime),
): CpuLoadSampler =
    FirstOperationalCpuLoadSampler(
        SmoothedCpuLoadSampler(oshiSampler, smoothingTimeNs, nanoClock),
        SmoothedCpuLoadSampler(processSampler, smoothingTimeNs, nanoClock),
        PerCoreCpuLoadSampler(systemLoadAverageSampler, processorCount),
    )
