package straightway.mosa.defense.cpu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
                    CpuSheddingConfig(
                        loadSheddingStart = 0.9,
                        loadSheddingFull = 0.9,
                    ),
                    ConstCpuLoadSampler(0.95),
                )

            val deciderResult = decider.decideShedding(SheddingRequest("GET", "/test"))

            assertEquals(1.0, deciderResult.sheddingFactor)
            assertEquals("CPU load per core above 0.9 (0.95)", deciderResult.reason)
        }

    @Test
    fun `sheds proportionally within range`() =
        runTest {
            val decider =
                CpuSheddingDecider(
                    CpuSheddingConfig(
                        loadSheddingStart = 0.6,
                        loadSheddingFull = 1.0,
                    ),
                    ConstCpuLoadSampler(0.8),
                )

            val deciderResult = decider.decideShedding(SheddingRequest("GET", "/test"))

            assertEquals(0.5, deciderResult.sheddingFactor, 1e-6)
            assertEquals("CPU load per core in shedding range 0.6-1.0 (0.8)", deciderResult.reason)
        }

    @Test
    fun `ignores negative sampler results`() =
        runTest {
            val decider =
                CpuSheddingDecider(
                    CpuSheddingConfig(
                        loadSheddingStart = 0.1,
                        loadSheddingFull = 0.1,
                    ),
                    ConstCpuLoadSampler(-0.5, isOperational = false),
                )

            val deciderResult = decider.decideShedding(SheddingRequest("GET", "/test"))

            assertEquals(SheddingDecider.Result.NO_SHEDDING, deciderResult)
        }

    @Test
    fun `sheds under generated cpu load with SystemLoadAverageCpuLoadSampler`() {
        `sheds under generated cpu load`(PerCoreCpuLoadSampler(SystemLoadAverageCpuLoadSampler()))
    }

    @Test
    fun `sheds under generated cpu load with OshiCpuLoadSampler`() {
        `sheds under generated cpu load`(SmoothedCpuLoadSampler(OshiCpuLoadSampler()))
    }

    @Test
    fun `sheds under generated cpu load with ProcessCpuLoadSampler`() {
        `sheds under generated cpu load`(SmoothedCpuLoadSampler(ProcessCpuLoadSampler()))
    }

    fun `sheds under generated cpu load`(gauge: CpuLoadSampler) =
        runTest {
            if (!gauge.isOperational) throw AssertionError("$gauge is not operational")
            withContext(Dispatchers.Default) {
                var threshold = 0.0
                while (threshold <= 0.0) {
                    delay(5)
                    threshold = gauge.sample() * 0.93 // Try with a low threshold
                }

                var loadThreads: List<Thread>? = null

                val isLoadActive = AtomicBoolean(true)
                try {
                    withTimeout(1.minutes) {
                        while (true) {
                            threshold *= 1.1
                            val decider =
                                CpuSheddingDecider(
                                    CpuSheddingConfig(
                                        loadSheddingStart = threshold,
                                        loadSheddingFull = threshold,
                                    ),
                                    gauge,
                                )

                            delay(5) // Calm down
                            if (decider.decideShedding(SheddingRequest("GET", "/test")).sheddingFactor == 1.0) {
                                continue
                            }

                            println()
                            println("Threshold for $gauge: $threshold")

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
