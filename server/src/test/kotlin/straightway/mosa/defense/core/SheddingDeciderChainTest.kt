package straightway.mosa.defense.core

import com.sun.management.OperatingSystemMXBean
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import straightway.mosa.NanoClock
import straightway.mosa.defense.cpu.CpuSheddingConfig
import straightway.mosa.defense.memory.MemorySheddingConfig
import straightway.mosa.defense.requestduration.RequestDurationForRouteState
import straightway.mosa.defense.requestduration.RequestDurationForRouteStateRegistry
import straightway.mosa.defense.requestduration.RequestDurationSheddingConfig
import straightway.mosa.defense.requestduration.RequestDurationSheddingRatioMapper
import java.lang.management.ManagementFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class SheddingDeciderChainTest {
    @Test
    fun `builds shedding decider chain with koin dependencies`() {
        val koinApplication =
            buildKoin(createOsBean())
        try {
            val decider = koinApplication.koin.sheddingDeciderChain()
            val result =
                runBlocking {
                    decider.decideShedding(
                        SheddingRequest("GET", "/health"),
                    )
                }
            assertNotNull(result)
        } finally {
            koinApplication.close()
        }
    }

    @Test
    fun `builds shedding decider chain without memory bean`() {
        mockkStatic(ManagementFactory::class)
        try {
            every { ManagementFactory.getMemoryMXBean() } returns null
            val koinApplication = buildKoin(createOsBean())
            try {
                val decider = koinApplication.koin.sheddingDeciderChain()
                runBlocking {
                    decider.decideShedding(
                        SheddingRequest("GET", "/health"),
                    )
                }
            } finally {
                koinApplication.close()
            }
        } finally {
            unmockkStatic(ManagementFactory::class)
        }
    }

    @Test
    fun `builds shedding decider chain without operating system bean`() {
        val koinApplication = buildKoin(null)
        try {
            val decider = koinApplication.koin.sheddingDeciderChain()
            runBlocking {
                decider.decideShedding(
                    SheddingRequest("GET", "/health"),
                )
            }
        } finally {
            koinApplication.close()
        }
    }

    private fun buildKoin(osBean: OperatingSystemMXBean?): KoinApplication =
        koinApplication {
            modules(
                module {
                    single<MeterRegistry> { SimpleMeterRegistry() }
                    osBean?.let { bean ->
                        single<OperatingSystemMXBean> { bean }
                    }
                    single { NanoClock { 0L } }
                    single { Random(123L) }
                    single {
                        LoadSheddingConfig(
                            cpuSheddingConfig =
                                CpuSheddingConfig(
                                    loadSheddingStart = 0.0,
                                    loadSheddingFull = 0.0,
                                ),
                            heapMemorySheddingConfig =
                                MemorySheddingConfig(
                                    memorySheddingStart = 0.0,
                                    memorySheddingFull = 0.0,
                                ),
                            nonHeapMemorySheddingConfig =
                                MemorySheddingConfig(
                                    memorySheddingStart = 0.0,
                                    memorySheddingFull = 0.0,
                                ),
                            systemMemorySheddingConfig =
                                MemorySheddingConfig(
                                    memorySheddingStart = 0.0,
                                    memorySheddingFull = 0.0,
                                ),
                            cpuLoadSmoothingHalfLifeNs = 250_000_000L,
                            loadSheddingRetryAfter = 1.seconds,
                        )
                    }
                    single {
                        RequestDurationSheddingConfig(
                            minSamplesForBaseline = 10,
                            minBaselineMs = 1.0,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 1.5,
                            sheddingSampleAlpha = 0.2,
                            sheddingDecayAlpha = 0.3,
                            sampleIntervalNs = 1_000_000L,
                        )
                    }
                    single { RequestDurationSheddingRatioMapper(get()) }
                    single {
                        RequestDurationForRouteState.Factory { timer ->
                            RequestDurationForRouteState(get(), get(), timer, get())
                        }
                    }
                    single { RequestDurationForRouteStateRegistry(get(), get()) }
                },
            )
        }

    @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
    private fun createOsBean(): OperatingSystemMXBean {
        val original = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
        return object : OperatingSystemMXBean by original {
            override fun getTotalMemorySize(): Long = 1024L

            override fun getFreeMemorySize(): Long = 256L

            override fun getProcessCpuTime(): Long = 0L
        }
    }
}
