package straightway.mosa.defense.core

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import straightway.mosa.defense.cpu.CpuSheddingConfig
import straightway.mosa.defense.cpu.CpuSheddingDecider
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
                ) { 1.0 }
            application {
                install(createMosaLoadSheddingPlugin(mockk())) {
                    this.decider = decider
                    retryAfter = 2.seconds
                }
                routing {
                    get("/health") { call.respondText("ok") }
                }
            }

            val response = client.get("/health")

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals("2", response.headers[HttpHeaders.RetryAfter])
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
                ) { 0.2 }
            application {
                install(createMosaLoadSheddingPlugin(mockk())) {
                    this.decider = decider
                }
                routing {
                    get("/health") { call.respondText("ok") }
                }
            }

            val response = client.get("/health")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("ok", response.bodyAsText())
        }
}
