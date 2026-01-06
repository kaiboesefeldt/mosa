package straightway.mosa.testutils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import straightway.mosa.jackson.InstantJacksonSerializers
import java.util.TimeZone

private val mapper =
    ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerModule(InstantJacksonSerializers.createModule())
        .setTimeZone(TimeZone.getTimeZone("UTC"))

fun HttpRequestBuilder.setJsonBody(body: Any) {
    val payload = mapper.writeValueAsBytes(body)
    contentType(ContentType.Application.Json)
    setBody(payload)
}

fun HttpRequestBuilder.setRawJsonBody(body: String) {
    val payload = body.encodeToByteArray()
    contentType(ContentType.Application.Json)
    setBody(payload)
}
