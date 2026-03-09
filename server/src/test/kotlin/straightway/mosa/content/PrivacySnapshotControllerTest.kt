package straightway.mosa.content

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test
import straightway.mosa.content.snapshot.FreezeSnapshotRestoreService
import straightway.mosa.content.snapshot.FreezeSnapshotService
import straightway.mosa.content.snapshot.InvalidFreezeSnapshotException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PrivacySnapshotControllerTest {
    private val test
        get() =
            object {
                val freezeSnapshotService = mockk<FreezeSnapshotService>()
                val restoreSnapshotService = mockk<FreezeSnapshotRestoreService>()
                val env =
                    ControllerHttpTestEnvironment(
                        PrivacySnapshotController(
                            freezeSnapshotService,
                            restoreSnapshotService,
                            Clock.fixed(Instant.ofEpochSecond(170000000L), ZoneId.of("UTC")),
                        ),
                    ) {
                        single { freezeSnapshotService }
                        single { restoreSnapshotService }
                    }
            }

    @Test
    fun `freeze returns snapshot download for authenticated user`() =
        with(test) {
            env {
                val snapshot = byteArrayOf(1, 2, 3, 4)
                coEvery { freezeSnapshotService.freeze(env.gid) } returns snapshot

                val response =
                    client.post("/v1/privacy/freeze") {
                        bearerAuth(env.bearerToken)
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(ContentType.Application.OctetStream.toString(), response.headers["Content-Type"])
                assertEquals(
                    ContentDisposition.Attachment
                        .withParameter(ContentDisposition.Parameters.FileName, "mosa-snapshot-1975-05-22.bin")
                        .toString(),
                    response.headers["Content-Disposition"],
                )
                assertContentEquals(snapshot, response.body<ByteArray>())
                coVerify(exactly = 1) { freezeSnapshotService.freeze(env.gid) }
            }
        }

    @Test
    fun `freeze rejects unauthorized access`() =
        with(test) {
            env {
                val response = client.post("/v1/privacy/freeze")

                assertEquals(HttpStatusCode.Unauthorized, response.status)
                coVerify(exactly = 0) { freezeSnapshotService.freeze(any()) }
            }
        }

    @Test
    fun `restore returns summary for authenticated user`() =
        with(test) {
            env {
                val snapshot = byteArrayOf(7, 8, 9)
                val result = FreezeSnapshotRestoreResult(listOf("post-1", "reply-2"), 2)
                coEvery { restoreSnapshotService.restore(env.gid, snapshot) } returns result

                val response =
                    client.post("/v1/privacy/restore") {
                        bearerAuth(env.bearerToken)
                        header(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                        setBody(snapshot)
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(result, response.body<FreezeSnapshotRestoreResult>())
                coVerify(exactly = 1) { restoreSnapshotService.restore(env.gid, snapshot) }
            }
        }

    @Test
    fun `restore translates invalid snapshot into bad request`() =
        with(test) {
            env {
                val snapshot = byteArrayOf(7, 8, 9)
                coEvery {
                    restoreSnapshotService.restore(env.gid, snapshot)
                } throws InvalidFreezeSnapshotException("corrupted snapshot")

                val response =
                    client.post("/v1/privacy/restore") {
                        bearerAuth(env.bearerToken)
                        header(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                        setBody(snapshot)
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertEquals(mapOf("message" to "corrupted snapshot"), response.body<Map<String, String>>())
            }
        }

    @Test
    fun `restore rejects unauthorized access`() =
        with(test) {
            env {
                val response =
                    client.post("/v1/privacy/restore") {
                        header(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                        setBody(byteArrayOf(1))
                    }

                assertEquals(HttpStatusCode.Unauthorized, response.status)
                coVerify(exactly = 0) { restoreSnapshotService.restore(any(), any()) }
            }
        }
}
