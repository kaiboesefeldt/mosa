package straightway.mosa.defense.cpu

import io.github.oshai.kotlinlogging.KotlinLogging
import straightway.mosa.NanoClock

fun defaultCpuLoadSampler(
    oshiSampler: CpuLoadSampler = OshiCpuLoadSampler(),
    processSampler: CpuLoadSampler = ProcessCpuLoadSampler(),
    systemLoadAverageSampler: CpuLoadSampler = SystemLoadAverageCpuLoadSampler(),
    processorCount: Int = Runtime.getRuntime().availableProcessors(),
    smoothingHalfLifeNs: Long = 250_000_000L,
    nanoClock: NanoClock = NanoClock(System::nanoTime),
): CpuLoadSampler =
    FirstOperationalCpuLoadSampler(
        SmoothedCpuLoadSampler(oshiSampler, smoothingHalfLifeNs, nanoClock),
        SmoothedCpuLoadSampler(processSampler, smoothingHalfLifeNs, nanoClock),
        PerCoreCpuLoadSampler(systemLoadAverageSampler, processorCount),
    ).also { logger.info { "CPU load sampler: $it" } }

private val logger = KotlinLogging.logger {}
