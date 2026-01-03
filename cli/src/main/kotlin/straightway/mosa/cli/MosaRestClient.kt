package straightway.mosa.cli

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import straightway.mosa.auth.AuthenticationApprovalRequest
import straightway.mosa.auth.JwtBuilder
import straightway.mosa.auth.PodCertificateResponse
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryResult
import java.net.URI
import java.net.URL
import java.security.KeyPair
import java.security.MessageDigest
import java.time.Clock
import java.time.ZoneId
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.hours

class MosaRestClient(
    private val podUrl: URL,
    private val identityKeyPair: KeyPair,
    private val httpClient: HttpClient,
) {
    suspend fun publish(publish: PublishRequest) {
        val jwt = createJWT()
        val response =
            httpClient.post(URI("$podUrl/v1/publish").toURL()) {
                contentType(ContentType.Application.Json)
                setBody(publish)
                bearerAuth(jwt)
            }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Publish request $publish got an error response: $response")
        }
    }

    suspend fun search(query: QueryRequest): QueryResult {
        val jwt = createJWT()
        val response =
            httpClient.post(URI("$podUrl/v1/query").toURL()) {
                contentType(ContentType.Application.Json)
                setBody(query)
                bearerAuth(jwt)
            }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Search request $query got an error response: $response")
        }

        return response.body<QueryResult>()
    }

    suspend fun approveGid(request: AuthenticationApprovalRequest): PodCertificateResponse {
        val response =
            httpClient.post(URI("$podUrl/v1/approvegid").toURL()) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Authorization request $request got an error response: $response")
        }

        return response.body<PodCertificateResponse>()
    }

    private suspend fun createJWT(): String {
        val hasher = MessageDigest.getInstance("SHA-256")
        val publicKeyHash =
            hasher.digest(
                identityKeyPair.public.encoded
                    .takeLast(32)
                    .toByteArray(),
            )
        val gid =
            Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT)
                .encode(publicKeyHash)
        val podCert = approveGid(AuthenticationApprovalRequest(gid)).certificate

        val jwtBuilder = JwtBuilder(Clock.system(ZoneId.of("GMT")), 1.hours)
        return jwtBuilder.buildMisJwt(
            identityKeyPair,
            podCert = podCert,
        )
    }
}
