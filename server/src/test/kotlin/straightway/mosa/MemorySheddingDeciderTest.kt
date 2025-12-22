package straightway.mosa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import straightway.mosa.defense.HeapUsageGauge
import straightway.mosa.defense.MemorySheddingDecider
import straightway.mosa.defense.SheddingDecider
import straightway.mosa.defense.SheddingRequest
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
                    memorySheddingThreshold = 0.9,
                ) { 0.95 }

            val deciderResult = decider.decideShedding(SheddingRequest("GET", "/test"))

            assertEquals(1.0, deciderResult.sheddingFactor)
            assertEquals("Heap usage above 0.9 (95.0%)", deciderResult.reason)
        }

    @Test
    fun `does not shed when heap usage is below threshold`() =
        runTest {
            val decider =
                MemorySheddingDecider(
                    memorySheddingThreshold = 0.9,
                ) { 0.5 }

            val deciderResult = decider.decideShedding(SheddingRequest("GET", "/test"))

            assertEquals(SheddingDecider.Result.NO_SHEDDING, deciderResult)
        }

    @Test
    fun `ignores negative sampler results`() =
        runTest {
            val decider =
                MemorySheddingDecider(
                    memorySheddingThreshold = 0.1,
                ) { -0.5 }

            val deciderResult = decider.decideShedding(SheddingRequest("GET", "/test"))

            assertEquals(SheddingDecider.Result.NO_SHEDDING, deciderResult)
        }

    @Test
    fun `ignores non-finite sampler results`() =
        runTest {
            val decider =
                MemorySheddingDecider(
                    memorySheddingThreshold = 0.1,
                ) { Double.NaN }

            val deciderResult = decider.decideShedding(SheddingRequest("GET", "/test"))

            assertEquals(SheddingDecider.Result.NO_SHEDDING, deciderResult)
        }

    @Test
    fun `sheds under allocated heap usage`() =
        runTest {
            withContext(Dispatchers.Default) {
                val gauge = HeapUsageGauge()
                val baseline = gauge.sample()
                val threshold = baseline * 1.2
                assertTrue(
                    baseline < threshold,
                    "Expected baseline to be below threshold (baseline=$baseline threshold=$threshold)",
                )

                val decider = MemorySheddingDecider(threshold, gauge::sample)
                val initialResult = decider.decideShedding(SheddingRequest("GET", "/test"))
                assertEquals(
                    0.0,
                    initialResult.sheddingFactor,
                    "Expected no shedding under baseline usage (baseline=$baseline threshold=$threshold reason=${initialResult.reason})",
                )

                val retained = mutableListOf<ByteArray>()

                withTimeout(1.minutes) {
                    while (decider.decideShedding(SheddingRequest("GET", "/test")).sheddingFactor == 0.0) {
                        retained.add(ByteArray(allocationChunkSizeBytes))
                        if (retained.size % 8 == 0) {
                            Thread.sleep(1)
                        }
                    }
                }

                assertTrue(retained.isNotEmpty())
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
}
