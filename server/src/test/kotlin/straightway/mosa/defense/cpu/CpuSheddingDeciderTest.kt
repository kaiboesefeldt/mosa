package straightway.mosa.defense.cpu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import straightway.mosa.defense.core.SheddingDecider
import straightway.mosa.defense.core.SheddingRequest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

class CpuSheddingDeciderTest {
    @Test
    fun `sheds when cpu load exceeds threshold`() =
        runTest {
            val decider =
                CpuSheddingDecider(
                    loadSheddingThreshold = 0.9,
                ) { 0.95 }

            val deciderResult = decider.decideShedding(SheddingRequest("GET", "/test"))

            assertEquals(1.0, deciderResult.sheddingFactor)
            assertEquals("CPU load per core above 0.9 (0.95)", deciderResult.reason)
        }

    @Test
    fun `ignores negative sampler results`() =
        runTest {
            val decider =
                CpuSheddingDecider(
                    loadSheddingThreshold = 0.1,
                ) { -0.5 }

            val deciderResult = decider.decideShedding(SheddingRequest("GET", "/test"))

            assertEquals(SheddingDecider.Result.NO_SHEDDING, deciderResult)
        }

    @Test
    fun `sheds under generated cpu load`() =
        runTest {
            withContext(Dispatchers.Default) {
                val gauge = CpuLoadGauge()
                var threshold = gauge.sample() * 0.93 // Try with a low threshold
                var loadThreads: List<Thread>? = null

                val isLoadActive = AtomicBoolean(true)
                try {
                    withTimeout(1.minutes) {
                        while (true) {
                            threshold *= 1.001
                            val decider = CpuSheddingDecider(threshold, gauge::sample)

                            Thread.sleep(5) // Calm down
                            if (decider.decideShedding(SheddingRequest("GET", "/test")).sheddingFactor == 1.0) {
                                continue
                            }

                            val workerCount = max(Runtime.getRuntime().availableProcessors() * 20, 20)
                            loadThreads =
                                (0 until workerCount).map { worker ->
                                    thread(start = true, isDaemon = true) {
                                        var value = worker.toLong()
                                        while (isLoadActive.get()) {
                                            value = value xor 1_234_567L
                                        }
                                    }.apply { name = "cpu-load-$worker" }
                                }

                            var value = 1234L
                            while (decider.decideShedding(SheddingRequest("GET", "/test")).sheddingFactor == 0.0) {
                                value = value xor 1_234_567L
                            }
                            isLoadActive.set(false)
                            return@withTimeout
                        }
                    }
                } finally {
                    isLoadActive.set(false)
                    loadThreads?.forEach { it.join() }
                }

                assertNotNull(loadThreads)
            }
        }
}
