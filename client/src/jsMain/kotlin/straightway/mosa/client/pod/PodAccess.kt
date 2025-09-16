package straightway.mosa.client.pod

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import straightway.mosa.auth.AuthenticationApprovalRequest
import straightway.mosa.auth.PodCertificate

class PodAccess(
    private val podBase: String,
    private val httpClient: HttpClient,
) {
    suspend fun approveGid(gid: String): PodCertificate {
        val url = "$podBase/v1/approvegid"
        val response =
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(AuthenticationApprovalRequest(gid))
            }
        if (!response.status.value.toString().startsWith("2")) {
            throw RuntimeException("Invalid response to $url with $gid: $response")
        }
        return response.body()
    }
}

fun KoinComponent.podAccess(podHost: String) = get<PodAccess> { parametersOf(podHost) }