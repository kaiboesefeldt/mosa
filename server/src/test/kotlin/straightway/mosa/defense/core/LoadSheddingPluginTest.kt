package straightway.mosa.defense.core

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.koin.core.Koin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.ktor.plugin.koin
import straightway.mosa.defense.cpu.ConstCpuLoadSampler
import straightway.mosa.defense.cpu.CpuSheddingConfig
import straightway.mosa.defense.cpu.CpuSheddingDecider
import straightway.mosa.defense.memory.MemorySheddingConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class LoadSheddingPluginTest {
    @Test
    fun `responds with 503 when shedding`() =
        testApplication {
            val decider =
                CpuSheddingDecider(
                    CpuSheddingConfig(
                        loadSheddingStart = 0.9,
                        loadSheddingFull = 0.9,
                    ),
                    ConstCpuLoadSampler(1.0),
                )
            var unmock: (() -> Unit)? = null
            try {
                application {
                    val koin = installLoadSheddingTestKoin(loadSheddingConfig(2.seconds))
                    unmock = mockDecider(koin, decider)
                    install(createMosaLoadSheddingPlugin())
                    routing {
                        get("/health") { call.respondText("ok") }
                    }
                }

                val response = client.get("/health")

                assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
                assertEquals("2", response.headers[HttpHeaders.RetryAfter])
            } finally {
                unmock?.invoke()
            }
        }

    @Test
    fun `passes requests when load is acceptable`() =
        testApplication {
            val decider =
                CpuSheddingDecider(
                    CpuSheddingConfig(
                        loadSheddingStart = 0.85,
                        loadSheddingFull = 0.85,
                    ),
                    ConstCpuLoadSampler(0.2),
                )
            var unmock: (() -> Unit)? = null
            try {
                application {
                    val koin = installLoadSheddingTestKoin(loadSheddingConfig())
                    unmock = mockDecider(koin, decider)
                    install(createMosaLoadSheddingPlugin())
                    routing {
                        get("/health") { call.respondText("ok") }
                    }
                }

                val response = client.get("/health")

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("ok", response.bodyAsText())
            } finally {
                unmock?.invoke()
            }
        }

    @Test
    fun `terminates pipeline when shedding`() =
        testApplication {
            val decider =
                CpuSheddingDecider(
                    CpuSheddingConfig(
                        loadSheddingStart = 0.9,
                        loadSheddingFull = 0.9,
                    ),
                    ConstCpuLoadSampler(1.0),
                )
            val callPhaseReached = AtomicBoolean(false)
            var unmock: (() -> Unit)? = null
            try {
                application {
                    val koin = installLoadSheddingTestKoin(loadSheddingConfig())
                    unmock = mockDecider(koin, decider)
                    install(createMosaLoadSheddingPlugin())
                    intercept(ApplicationCallPipeline.Call) {
                        callPhaseReached.set(true)
                        proceed()
                    }
                    routing {
                        get("/health") { call.respondText("ok") }
                    }
                }

                val response = client.get("/health")

                assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
                assertEquals(false, callPhaseReached.get())
            } finally {
                unmock?.invoke()
            }
        }

    @Test
    fun `executes route when shedding is disabled`() =
        testApplication {
            val decider =
                CpuSheddingDecider(
                    CpuSheddingConfig(
                        loadSheddingStart = 0.9,
                        loadSheddingFull = 0.9,
                    ),
                    ConstCpuLoadSampler(0.2),
                )
            val routeReached = AtomicBoolean(false)
            var unmock: (() -> Unit)? = null
            try {
                application {
                    val koin = installLoadSheddingTestKoin(loadSheddingConfig())
                    unmock = mockDecider(koin, decider)
                    install(createMosaLoadSheddingPlugin())
                    routing {
                        get("/health") {
                            routeReached.set(true)
                            call.respondText("ok")
                        }
                    }
                }

                val response = client.get("/health")

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(true, routeReached.get())
            } finally {
                unmock?.invoke()
            }
        }

    @Test
    fun `keeps shedding without repeating start log`() =
        testApplication {
            val decider =
                CpuSheddingDecider(
                    CpuSheddingConfig(
                        loadSheddingStart = 0.9,
                        loadSheddingFull = 0.9,
                    ),
                    ConstCpuLoadSampler(1.0),
                )
            var unmock: (() -> Unit)? = null
            try {
                application {
                    val koin = installLoadSheddingTestKoin(loadSheddingConfig())
                    unmock = mockDecider(koin, decider)
                    install(createMosaLoadSheddingPlugin())
                    routing {
                        get("/health") { call.respondText("ok") }
                    }
                }

                val firstResponse = client.get("/health")
                val secondResponse = client.get("/health")

                assertEquals(HttpStatusCode.ServiceUnavailable, firstResponse.status)
                assertEquals(HttpStatusCode.ServiceUnavailable, secondResponse.status)
            } finally {
                unmock?.invoke()
            }
        }

    @Test
    fun `skips shedding when random selection misses`() =
        testApplication {
            val decider =
                SheddingDecider {
                    SheddingDecider.Result(0.5, "half")
                }
            var unmock: (() -> Unit)? = null
            try {
                application {
                    val koin =
                        installLoadSheddingTestKoin(
                            loadSheddingConfig(),
                            FixedRandom(0.75),
                        )
                    unmock = mockDecider(koin, decider)
                    install(createMosaLoadSheddingPlugin())
                    routing {
                        get("/health") { call.respondText("ok") }
                    }
                }

                val response = client.get("/health")

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("ok", response.bodyAsText())
            } finally {
                unmock?.invoke()
            }
        }

    @Test
    fun `stops shedding when load recovers`() =
        testApplication {
            val calls = AtomicBoolean(false)
            val decider =
                SheddingDecider {
                    if (calls.compareAndSet(false, true)) {
                        SheddingDecider.Result(1.0, "overloaded")
                    } else {
                        SheddingDecider.Result.NO_SHEDDING
                    }
                }
            val routeReached = AtomicBoolean(false)
            var unmock: (() -> Unit)? = null
            try {
                application {
                    val koin = installLoadSheddingTestKoin(loadSheddingConfig())
                    unmock = mockDecider(koin, decider)
                    install(createMosaLoadSheddingPlugin())
                    routing {
                        get("/health") {
                            routeReached.set(true)
                            call.respondText("ok")
                        }
                    }
                }

                val firstResponse = client.get("/health")
                val secondResponse = client.get("/health")

                assertEquals(HttpStatusCode.ServiceUnavailable, firstResponse.status)
                assertEquals(HttpStatusCode.OK, secondResponse.status)
                assertEquals(true, routeReached.get())
            } finally {
                unmock?.invoke()
            }
        }

    private fun mockDecider(
        koin: Koin,
        decider: SheddingDecider,
    ): () -> Unit {
        mockkStatic("straightway.mosa.defense.core.LoadSheddingPluginKt")
        every { koin.sheddingDeciderChain() } returns decider
        return { unmockkStatic("straightway.mosa.defense.core.LoadSheddingPluginKt") }
    }

    private fun Application.installLoadSheddingTestKoin(loadSheddingConfig: LoadSheddingConfig): Koin {
        install(Koin) {
            allowOverride(true)
            modules(
                module {
                    single { Random(12345L) }
                    single { loadSheddingConfig }
                },
            )
        }
        return koin()
    }

    private fun Application.installLoadSheddingTestKoin(
        loadSheddingConfig: LoadSheddingConfig,
        random: Random,
    ): Koin {
        install(Koin) {
            allowOverride(true)
            modules(
                module {
                    single { random }
                    single { loadSheddingConfig }
                },
            )
        }
        return koin()
    }

    private fun loadSheddingConfig(retryAfter: kotlin.time.Duration = 30.seconds) =
        LoadSheddingConfig(
            cpuSheddingConfig =
                CpuSheddingConfig(
                    loadSheddingStart = Double.MAX_VALUE,
                    loadSheddingFull = Double.MAX_VALUE,
                ),
            heapMemorySheddingConfig =
                MemorySheddingConfig(
                    memorySheddingStart = Double.MAX_VALUE,
                    memorySheddingFull = Double.MAX_VALUE,
                ),
            nonHeapMemorySheddingConfig =
                MemorySheddingConfig(
                    memorySheddingStart = Double.MAX_VALUE,
                    memorySheddingFull = Double.MAX_VALUE,
                ),
            systemMemorySheddingConfig =
                MemorySheddingConfig(
                    memorySheddingStart = Double.MAX_VALUE,
                    memorySheddingFull = Double.MAX_VALUE,
                ),
            cpuLoadSmoothingHalfLifeNs = 250_000_000L,
            loadSheddingRetryAfter = retryAfter,
        )

    private class FixedRandom(
        private val value: Double,
    ) : Random() {
        override fun nextBits(bitCount: Int): Int = 0

        override fun nextDouble(): Double = value
    }
}
