package straightway.mosa.defense.cpu

import com.sun.management.OperatingSystemMXBean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import straightway.mosa.NanoClock
import straightway.mosa.defense.core.SheddingDecider
import straightway.mosa.defense.core.SheddingRequest
import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
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
    fun `sheds under generated cpu load with ProcessCpuTimeSampler`() {
        runBlocking(Dispatchers.Default) {
            val lastSample = AtomicReference(Double.NaN)
            val loggingSampler = createLoggingSampler(lastSample)
            assertTrue { loggingSampler.isOperational }

            println("Determining initial threshold")
            val baseline = sampleBaseline(loggingSampler)
            val loadState = startLoadState()
            val loadThreads = mutableListOf<Thread>()
            try {
                withTimeout(1.minutes) {
                    val decider = createDeciderAndAssertNoShedding(loggingSampler, baseline, lastSample)
                    waitForShedding(decider, lastSample, loadState, loadThreads)
                    stopLoad(loadState)
                }
            } finally {
                stopLoad(loadState)
                loadThreads.forEach { it.join() }
            }

            assertNotNull(loadThreads)
            assertTrue(loadThreads.isNotEmpty())
        }
    }

    private fun createLoggingSampler(lastSample: AtomicReference<Double>): CpuLoadSampler {
        val sampler =
            SmoothedCpuLoadSampler(
                ProcessCpuTimeSampler(
                    nanoClock = NanoClock(System::nanoTime),
                    numberOfProcessors = Runtime.getRuntime().availableProcessors(),
                    minSampleIntervalNs = 1000L,
                    osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean,
                ),
                halfLifeNs = 1_000_000_000L,
            )
        return object : CpuLoadSampler by sampler {
            override fun sample(): Double = sampler.sample().also { lastSample.set(it) }
        }
    }

    private suspend fun sampleBaseline(sampler: CpuLoadSampler): Double {
        var load = 0.0
        while (true) {
            delay(100)
            load = sampler.sample()
            if (0 < load && load < 0.8) break
        }
        println("Baseline cpu load: $load")
        return load
    }

    private tailrec suspend fun createDeciderAndAssertNoShedding(
        sampler: CpuLoadSampler,
        baseline: Double,
        lastSample: AtomicReference<Double>,
    ): CpuSheddingDecider {
        val threshold = (baseline * 1.01).coerceAtLeast(0.05)
        println("Shedding threshold: $threshold")
        assertTrue(threshold < 1.0)

        val decider =
            CpuSheddingDecider(
                CpuSheddingConfig(
                    loadSheddingStart = threshold,
                    loadSheddingFull = threshold,
                ),
                sampler,
            )
        val initialShedding =
            decider
                .decideShedding(
                    SheddingRequest(
                        "GET",
                        "/test",
                    ),
                ).sheddingFactor
        println("CPU load = ${lastSample.get()} -> initial shedding factor = $initialShedding")
        if (initialShedding == 0.0) return decider

        // Calm down CPU
        @Suppress("BlockingMethodInNonBlockingContext")
        Thread.sleep(100)

        return createDeciderAndAssertNoShedding(sampler, threshold, lastSample)
    }

    private suspend fun waitForShedding(
        decider: CpuSheddingDecider,
        lastSample: AtomicReference<Double>,
        isLoadActive: AtomicBoolean,
        loadThreads: MutableList<Thread>,
    ) {
        var value = 1234.0
        val maxThreads = max(Runtime.getRuntime().availableProcessors() * 6, 6)
        val threadsPerStep = max(1, maxThreads / 6)
        var nextIncreaseAt = System.nanoTime()
        addCpuLoadThreads(isLoadActive, loadThreads, threadsPerStep)
        while (true) {
            val sheddingFactor =
                decider
                    .decideShedding(
                        SheddingRequest(
                            "GET",
                            "/test",
                        ),
                    ).sheddingFactor
            println("CPU load = ${lastSample.get()} -> shedding factor = $sheddingFactor")
            if (sheddingFactor != 0.0) {
                println("done")
                return
            }
            if (loadThreads.size < maxThreads && System.nanoTime() >= nextIncreaseAt) {
                addCpuLoadThreads(
                    isLoadActive,
                    loadThreads,
                    min(threadsPerStep, maxThreads - loadThreads.size),
                )
                nextIncreaseAt = System.nanoTime() + 250_000_000L
            }
            repeat(20_000) {
                value = Math.fma(value, 1.0000001, 0.0000001)
            }
            delay(25)
        }
    }

    private fun startLoadState(): AtomicBoolean = AtomicBoolean(true)

    private fun stopLoad(isLoadActive: AtomicBoolean) {
        isLoadActive.set(false)
    }

    private fun addCpuLoadThreads(
        isLoadActive: AtomicBoolean,
        loadThreads: MutableList<Thread>,
        threadCount: Int,
    ) {
        if (threadCount <= 0) {
            return
        }
        val startIndex = loadThreads.size
        val startLatch = CountDownLatch(1)
        val sink = AtomicLong(0L)
        val threads =
            (startIndex until startIndex + threadCount).map { worker ->
                thread(start = true, isDaemon = true) {
                    try {
                        startLatch.await()
                    } catch (_: InterruptedException) {
                        return@thread
                    }
                    var value = worker.toDouble() + 1.0
                    while (isLoadActive.get()) {
                        repeat(2_0000) {
                            value = Math.sin(value) + Math.cos(value)
                        }
                        if (value.isNaN() || value.isInfinite()) {
                            value = worker.toDouble() + 1.0
                        }
                        sink.lazySet(value.toRawBits())
                    }
                }.apply { this.name = "cpu-load-$worker" }
            }
        startLatch.countDown()
        loadThreads.addAll(threads)
    }
}
