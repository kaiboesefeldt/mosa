package straightway.mosa.cli

import com.auth0.jwt.JWT
import straightway.mosa.authorization.JwtEd25519Algorithm
import straightway.mosa.authorization.PodCertificate
import java.security.KeyPair
import java.time.Clock
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class JwtBuilder(
    private val clock: Clock,
    private val jwtLifeTime: Duration
) {
    fun buildMisJwt(keyPair: KeyPair, podCert: PodCertificate) = JWT.create()
        .withPayload(
            mapOf(
                "pub" to keyPair.toBase64(),
                "cert" to podCert.toMap(),
                "exp" to (clock.instant() + jwtLifeTime.toJavaDuration()).epochSecond,
                "mvs" to "1"
            )
        )
        .sign(JwtEd25519Algorithm(keyPair))

    private fun KeyPair.toBase64(): String =
        Base64.Default.UrlSafe
            .withPadding(Base64.PaddingOption.ABSENT)
            .encode(this.public.encoded)

    private fun PodCertificate.toMap(): Map<String, Any> {
        return mapOf(
            "gid" to gid,
            "exp" to exp,
            "sig" to sig,
        ) + if (inf === null) emptyMap() else mapOf<String, Any>("inf" to inf!!)
    }

}