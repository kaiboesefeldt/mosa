package straightway.mosa.defense

import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.defense.core.LoadSheddingConfig
import straightway.mosa.defense.cpu.CpuSheddingConfig
import straightway.mosa.defense.memory.MemorySheddingConfig
import straightway.mosa.defense.requestguard.RequestBodyLimitConfig
import straightway.mosa.defense.requestguard.defaultMaxRequestBodyBytes
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

fun buildLoadSheddingConfig(argumentStore: CommandLineArgumentStore): LoadSheddingConfig {
    val loadSheddingStart = argumentStore.getDouble(DefenseCommandLineOptions.CPU_LOAD_SHEDDING_START) ?: 0.85
    val loadSheddingFull = argumentStore.getDouble(DefenseCommandLineOptions.CPU_LOAD_SHEDDING_FULL) ?: 0.95
    val cpuLoadSmoothingHalfLifeSeconds =
        argumentStore.getDouble(DefenseCommandLineOptions.CPU_LOAD_SMOOTHING_HALF_LIFE_SECONDS) ?: 10.0
    val cpuLoadSmoothingHalfLifeNs =
        (cpuLoadSmoothingHalfLifeSeconds * 1_000_000_000.0).roundToLong()
    val heapMemorySheddingStart = argumentStore.getDouble(DefenseCommandLineOptions.MEMORY_SHEDDING_HEAP_START) ?: 0.9
    val heapMemorySheddingFull = argumentStore.getDouble(DefenseCommandLineOptions.MEMORY_SHEDDING_HEAP_FULL) ?: 0.98
    val nonHeapMemorySheddingStart =
        argumentStore.getDouble(DefenseCommandLineOptions.MEMORY_SHEDDING_NONHEAP_START) ?: 0.98
    val nonHeapMemorySheddingFull =
        argumentStore.getDouble(DefenseCommandLineOptions.MEMORY_SHEDDING_NONHEAP_FULL) ?: 0.999
    val systemMemorySheddingStart = argumentStore.getDouble(DefenseCommandLineOptions.MEMORY_SHEDDING_SYSTEM_START) ?: 0.85
    val systemMemorySheddingFull = argumentStore.getDouble(DefenseCommandLineOptions.MEMORY_SHEDDING_SYSTEM_FULL) ?: 0.93
    val loadSheddingRetryAfterSeconds = argumentStore.getInt(DefenseCommandLineOptions.CPU_LOAD_SHEDDING_RETRY_AFTER) ?: 30
    return LoadSheddingConfig(
        cpuSheddingConfig =
            CpuSheddingConfig(
                loadSheddingStart = loadSheddingStart,
                loadSheddingFull = loadSheddingFull,
            ),
        heapMemorySheddingConfig =
            MemorySheddingConfig(
                memorySheddingStart = heapMemorySheddingStart,
                memorySheddingFull = heapMemorySheddingFull,
            ),
        nonHeapMemorySheddingConfig =
            MemorySheddingConfig(
                memorySheddingStart = nonHeapMemorySheddingStart,
                memorySheddingFull = nonHeapMemorySheddingFull,
            ),
        systemMemorySheddingConfig =
            MemorySheddingConfig(
                memorySheddingStart = systemMemorySheddingStart,
                memorySheddingFull = systemMemorySheddingFull,
            ),
        cpuLoadSmoothingHalfLifeNs = cpuLoadSmoothingHalfLifeNs,
        loadSheddingRetryAfter = loadSheddingRetryAfterSeconds.seconds,
    )
}

fun buildRequestBodyLimitConfig(
    argumentStore: CommandLineArgumentStore,
    maxFetchItems: Int,
): RequestBodyLimitConfig {
    val maxRequestBodyBytes =
        argumentStore.getInt(DefenseCommandLineOptions.MAX_REQUEST_BODY_BYTES)?.toLong()
            ?: defaultMaxRequestBodyBytes(maxFetchItems)
    return RequestBodyLimitConfig(maxBodyBytes = maxRequestBodyBytes)
}
