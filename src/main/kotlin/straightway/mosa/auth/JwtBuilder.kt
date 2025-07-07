package straightway.mosa.auth

import com.auth0.jwt.JWT
import java.security.KeyPair
import java.security.PublicKey
import java.time.Clock
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class JwtBuilder(
    private val clock: Clock,
    private val jwtLifeTime: Duration,
) {
    fun buildMisJwt(
        keyPair: KeyPair,
        podCert: PodCertificate,
    ): String =
        JWT
            .create()
            .withHeader(
                mapOf(
                    "alg" to "ed25519",
                    "typ" to "JWT",
                ),
            ).withPayload(
                mapOf(
                    "pub" to keyPair.public.toBase64(),
                    "cert" to podCert.toMap(),
                    "exp" to (clock.instant() + jwtLifeTime.toJavaDuration()).epochSecond,
                    "mvs" to "1",
                ),
            ).sign(JwtEd25519Algorithm(keyPair))

    private fun PublicKey.toBase64(): String =
        Base64.Default.UrlSafe
            .withPadding(Base64.PaddingOption.ABSENT)
            .encode(this.encoded.takeLast(32).toByteArray())

    private fun PodCertificate.toMap(): Map<String, Any> =
        mapOf(
            "gid" to gid,
            "exp" to exp,
            "sig" to sig,
        ) + if (inf === null) emptyMap() else mapOf<String, Any>("inf" to inf)
}
