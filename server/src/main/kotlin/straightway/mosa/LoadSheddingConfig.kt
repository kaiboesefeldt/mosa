package straightway.mosa

import straightway.mosa.defense.cpu.CpuSheddingConfig
import straightway.mosa.defense.memory.MemorySheddingConfig
import kotlin.time.Duration

data class LoadSheddingConfig(
    val cpuSheddingConfig: CpuSheddingConfig,
    val heapMemorySheddingConfig: MemorySheddingConfig,
    val nonHeapMemorySheddingConfig: MemorySheddingConfig,
    val systemMemorySheddingConfig: MemorySheddingConfig,
    val cpuLoadSmoothingHalfLifeNs: Long,
    val loadSheddingRetryAfter: Duration,
)
