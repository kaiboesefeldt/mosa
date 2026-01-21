package straightway.mosa.defense.memory

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import straightway.mosa.defense.core.SheddingDecider
import straightway.mosa.defense.core.SheddingRequest
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.lang.management.MemoryUsage
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class MemorySheddingDeciderTest {
    @Test
    fun `sheds when heap usage exceeds threshold`() =
        runTest {
            val decider =
                MemorySheddingDecider(
                    MemorySheddingConfig(
                        memorySheddingStart = 0.9,
                        memorySheddingFull = 0.9,
                    ),
                    ConstMemorySampler(0.95),
                )

            val deciderResult = decider.decideShedding(SheddingRequest("GET", "/test"))

            assertEquals(1.0, deciderResult.sheddingFactor)
            assertEquals("Memory usage above 0.9 (95.0%)", deciderResult.reason)
        }

    @Test
    fun `sheds proportionally within range`() =
        runTest {
            val memorySampler = ConstMemorySampler(0.8)
            val decider =
                MemorySheddingDecider(
                    MemorySheddingConfig(
                        memorySheddingStart = 0.7,
                        memorySheddingFull = 0.9,
                    ),
                    memorySampler,
                )

            val deciderResult = decider.decideShedding(SheddingRequest("GET", "/test"))

            assertEquals(0.5, deciderResult.sheddingFactor, 1e-6)
            assertEquals("$memorySampler in shedding range 0.7-0.9 (80.0%)", deciderResult.reason)
        }

    @Test
    fun `does not shed when heap usage is below threshold`() =
        runTest {
            val decider =
                MemorySheddingDecider(
                    MemorySheddingConfig(
                        memorySheddingStart = 0.9,
                        memorySheddingFull = 0.9,
                    ),
                    ConstMemorySampler(0.5),
                )

            val deciderResult = decider.decideShedding(SheddingRequest("GET", "/test"))

            assertEquals(SheddingDecider.Result.NO_SHEDDING, deciderResult)
        }

    @Test
    fun `ignores negative sampler results`() =
        runTest {
            val decider =
                MemorySheddingDecider(
                    MemorySheddingConfig(
                        memorySheddingStart = 0.1,
                        memorySheddingFull = 0.1,
                    ),
                    ConstMemorySampler(-0.5),
                )

            val deciderResult = decider.decideShedding(SheddingRequest("GET", "/test"))

            assertEquals(SheddingDecider.Result.NO_SHEDDING, deciderResult)
        }

    @Test
    fun `ignores non-finite sampler results`() =
        runTest {
            val decider =
                MemorySheddingDecider(
                    MemorySheddingConfig(
                        memorySheddingStart = 0.1,
                        memorySheddingFull = 0.1,
                    ),
                    ConstMemorySampler(Double.NaN),
                )

            val deciderResult = decider.decideShedding(SheddingRequest("GET", "/test"))

            assertEquals(SheddingDecider.Result.NO_SHEDDING, deciderResult)
        }

    @Test
    fun `does not shed when sampler is unavailable`() =
        runTest {
            val decider =
                MemorySheddingDecider(
                    MemorySheddingConfig(
                        memorySheddingStart = 0.1,
                        memorySheddingFull = 0.1,
                    ),
                    ConstMemorySampler(0.9, isAvailable = false),
                )

            val deciderResult = decider.decideShedding(SheddingRequest("GET", "/test"))

            assertEquals(SheddingDecider.Result.NO_SHEDDING, deciderResult)
        }

    @Test
    fun `sheds under allocated heap usage`() =
        runTest {
            withContext(Dispatchers.Default) {
                withTimeout(1.minutes) {
                    val memoryBean = ManagementFactory.getMemoryMXBean()
                    val gauge = GenericMemoryUsageSampler("Test") { memoryBean.heapMemoryUsage }
                    val decider = createNotSheddingDecider(gauge)

                    val retained = mutableListOf<ByteArray>()

                    while (decider
                            .decideShedding(
                                SheddingRequest(
                                    "GET",
                                    "/test",
                                ),
                            ).sheddingFactor == 0.0
                    ) {
                        retained.add(ByteArray(allocationChunkSizeBytes))
                        if (retained.size % 8 == 0) {
                            Thread.sleep(1)
                        }
                    }

                    assertTrue(retained.isNotEmpty())
                }
            }
        }

    @Test
    fun `uses committed heap when max is unavailable`() {
        val memoryBean =
            mockk<MemoryMXBean> {
                every { heapMemoryUsage } returns MemoryUsage(0L, 50L, 200L, -1L)
            }

        val gauge = GenericMemoryUsageSampler("Test") { memoryBean.heapMemoryUsage }

        assertEquals(0.25, gauge.sample(), 1e-6)
    }

    @Test
    fun `returns NaN when heap usage has no committed or max`() {
        val memoryBean =
            mockk<MemoryMXBean> {
                every { heapMemoryUsage } returns MemoryUsage(0L, 0L, 0L, -1L)
            }

        val gauge = GenericMemoryUsageSampler("Test") { memoryBean.heapMemoryUsage }

        assertTrue(gauge.sample().isNaN())
    }

    private suspend fun createNotSheddingDecider(gauge: GenericMemoryUsageSampler): MemorySheddingDecider {
        val baseline = max(0.1, gauge.sample())
        var threshold = baseline * 1.1
        var decider =
            MemorySheddingDecider(
                MemorySheddingConfig(
                    memorySheddingStart = threshold,
                    memorySheddingFull = threshold,
                ),
                gauge,
            )
        lateinit var initialResult: SheddingDecider.Result
        while (true) {
            initialResult = decider.decideShedding(SheddingRequest("GET", "/test"))
            if (initialResult.sheddingFactor <= 0.0) return decider
            threshold *= 1.1
            decider =
                MemorySheddingDecider(
                    MemorySheddingConfig(
                        memorySheddingStart = threshold,
                        memorySheddingFull = threshold,
                    ),
                    gauge,
                )
        }
    }

    private val allocationChunkSizeBytes by lazy {
        val oneMiB = 1024 * 1024
        val sixteenMiB = 16 * oneMiB
        val maxMemoryBytes = max(Runtime.getRuntime().maxMemory(), 1L)
        (maxMemoryBytes / 64)
            .coerceIn(oneMiB.toLong(), sixteenMiB.toLong())
            .toInt()
    }

    private data class ConstMemorySampler(
        val sample: Double,
        override val isAvailable: Boolean = true,
    ) : MemoryUsageSampler {
        override fun sample() = sample
    }
}
