package straightway.mosa.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.Header
import com.auth0.jwt.interfaces.Payload
import straightway.mosa.Crypto
import straightway.mosa.HasherFactory
import straightway.mosa.SignatureVerifierProvider
import straightway.mosa.TokenUtil
import java.time.Clock
import kotlin.io.encoding.Base64

class Auth0TokenUtil(
    val crypto: Crypto,
    val podCertificateService: PodCertificateService,
    val clock: Clock,
    val hasherFactory: HasherFactory,
    val signatureVerifierProvider: SignatureVerifierProvider,
) : TokenUtil {
    override fun decode(jwt: String): DecodedJWT = JWT.decode(jwt)

    override fun isValidMisJwt(jwt: DecodedJWT): Boolean =
        runCatching {
            jwt.isJwtSignatureValid() && jwt.isHeaderValid() && jwt.isPayloadValid()
        }.getOrDefault(false)

    private fun DecodedJWT.isJwtSignatureValid(): Boolean {
        val userPublicKeyBase64 = getClaim("pub").asString() ?: return false
        val pubKey = runCatching { crypto.deserializePublicKey(userPublicKeyBase64) }.getOrNull() ?: return false
        val headerAndPayload = this.header + "." + this.payload
        val signatureBytes =
            Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT)
                .decode(this.signature)
        val signature = signatureVerifierProvider.get()
        signature.initVerify(pubKey)
        signature.update(headerAndPayload.toByteArray(Charsets.US_ASCII))
        return signature.verify(signatureBytes)
    }

    private fun Header.isHeaderValid(): Boolean {
        if (algorithm != "ed25519") return false
        if (type != "JWT") return false
        if (contentType !== null) return false
        if (keyId !== null) return false
        return true
    }

    private fun Payload.isPayloadValid(): Boolean {
        val cert = getPodCertificate() ?: return false
        if (!cert.isValid()) return false

        if (getClaim("mvs").asString() != "1") return false
        if (hasSurplusClaims()) return false

        val jwtExp = getClaim("exp").asLong() ?: return false
        if (jwtExp < clock.instant().epochSecond) return false

        val pub =
            Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT)
                .decode(getClaim("pub").asString())
        val pubKeyHash =
            Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT)
                .encode(hasherFactory().digest(pub))
        if (pubKeyHash != cert.gid) return false

        return true
    }

    private fun Payload.hasSurplusClaims(): Boolean =
        claims.keys
            .minus(setOf("pub", "cert", "exp", "mvs"))
            .any()

    private fun PodCertificate.isValid(): Boolean = podCertificateService.isValid(this)

    private fun Payload.getPodCertificate(): PodCertificate? = getClaim("cert").asMap()?.toPodCertificate()
}
