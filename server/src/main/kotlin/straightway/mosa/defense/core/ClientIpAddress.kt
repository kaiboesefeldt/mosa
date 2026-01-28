package straightway.mosa.defense.core

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import straightway.mosa.auth.ClientIpResolver

internal fun ApplicationCall.clientIpAddress(clientIpResolver: ClientIpResolver): String =
    clientIpResolver.resolve(
        remoteHost = request.local.remoteHost,
        forwardedFor = request.headers[HttpHeaders.XForwardedFor],
    )
