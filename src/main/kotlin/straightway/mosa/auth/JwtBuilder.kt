package straightway.mosa.auth

import com.auth0.jwt.JWT
import straightway.mosa.crypto.toBase64
import java.security.KeyPair
import java.time.Clock
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
}
