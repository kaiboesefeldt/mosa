package straightway.mosa.client.pod

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import straightway.mosa.auth.AuthenticationApprovalRequest
import straightway.mosa.auth.PodCertificate
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryResult
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.ReplyRequest

class PodAccess(
    private val podBase: String,
    private val httpClient: HttpClient,
) {
    suspend fun approveGid(gid: String): PodCertificate {
        val path = "/v1/approvegid"
        return post(path, AuthenticationApprovalRequest(gid))
            .ensureSuccess(path, gid)
            .body()
    }

    suspend fun publish(request: PublishRequest, jwt: String) {
        val path = "/v1/publish"
        post(path, request, jwt).ensureSuccess(path, request)
    }

    suspend fun reply(request: ReplyRequest, jwt: String) {
        val path = "/v1/reply"
        post(path, request, jwt).ensureSuccess(path, request)
    }

    suspend fun query(request: QueryRequest, jwt: String): QueryResult {
        val path = "/v1/query"
        return post(path, request, jwt)
            .ensureSuccess(path, request)
            .body()
    }

    suspend fun queryOn(request: QueryOnRequest, jwt: String): QueryResult {
        val path = "/v1/queryon"
        return post(path, request, jwt)
            .ensureSuccess(path, request)
            .body()
    }

    private suspend fun post(path: String, payload: Any, jwt: String? = null): HttpResponse =
        httpClient.post("$podBase$path") {
            contentType(ContentType.Application.Json)
            setBody(payload)
            if (jwt != null)
                header(HttpHeaders.Authorization, "Bearer $jwt")
        }

    private fun HttpResponse.ensureSuccess(path: String, payload: Any): HttpResponse =
        apply {
            if (status.value !in 200..299) {
                throw RuntimeException("Invalid response to ${podBase}$path with $payload: $this")
            }
        }
}

fun KoinComponent.podAccess(podHost: String) = get<PodAccess> { parametersOf(podHost) }
