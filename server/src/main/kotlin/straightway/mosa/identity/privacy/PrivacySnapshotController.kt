package straightway.mosa.identity.privacy

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import straightway.mosa.base.BaseController
import straightway.mosa.identity.privacy.snapshot.FreezeSnapshotRestoreService
import straightway.mosa.identity.privacy.snapshot.FreezeSnapshotService
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class PrivacySnapshotController(
    private val freezeSnapshotService: FreezeSnapshotService,
    private val restoreSnapshotService: FreezeSnapshotRestoreService,
    private val clock: Clock,
) : BaseController() {
    override fun Route.register() {
        authenticate {
            postRoute("/privacy/freeze") {
                val gid = userGidOrRespondUnauthorized() ?: return@postRoute
                val snapshot = freezeSnapshotService.freeze(gid)
                call.response.header(
                    "Content-Disposition",
                    ContentDisposition.Attachment
                        .withParameter(ContentDisposition.Parameters.FileName, snapshotFileName())
                        .toString(),
                )
                call.respondBytes(snapshot, ContentType.Application.OctetStream)
            }
            postRoute("/privacy/restore") {
                val gid = userGidOrRespondUnauthorized() ?: return@postRoute
                val snapshot = call.receive<ByteArray>()
                val result = restoreSnapshotService.restore(gid, snapshot)
                call.respond(result)
            }
        }
    }

    private fun snapshotFileName(): String =
        "mosa-snapshot-${DateTimeFormatter.ISO_LOCAL_DATE.format(clock.instant().atZone(ZoneOffset.UTC))}.bin"
}
