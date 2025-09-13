package straightway.mosa.client

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit
import kotlin.js.json

class PodAccess(private val podBase: String) {
    suspend fun approveGid(gid: String): PodCertificate {
        val url = "$podBase/v1/approvegid"
        val request = RequestInit(
            method = "POST",
            headers = Headers().apply {
                append("Content-Type", "application/json; charset=utf-8")
                append("Accept", "application/json")
            },
            body = JSON.stringify(json("gid" to gid))
        )
        val response = window.fetch(url, request).await()
        if (!response.ok) throw RuntimeException("Invalid response to $url with $gid: $response")
        val obj = response.json().await()
        return PodCertificate(obj)
    }
}
