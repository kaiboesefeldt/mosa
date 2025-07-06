package straightway.mosa.crypto

import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.spec.NamedParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64

class CryptoEd25519 {
    init {
        Security.addProvider(BouncyCastleProvider())
    }

    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("Ed25519", "BC")
        kpg.initialize(NamedParameterSpec("Ed25519"))
        return kpg.generateKeyPair()
    }

    fun serializeKeyPair(keyPair: KeyPair): String {
        val privBytes = keyPair.private.encoded
        return Base64.UrlSafe
            .withPadding(Base64.PaddingOption.ABSENT)
            .encode(privBytes)
    }

    fun deserializeKeyPair(base64: String): KeyPair {
        val privBytes =
            Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT)
                .decode(base64)

        val privInfo = PrivateKeyInfo.getInstance(privBytes)

        val seed = (privInfo.parsePrivateKey().toASN1Primitive() as ASN1OctetString).octets

        val privParams = Ed25519PrivateKeyParameters(seed, 0)
        val pubParams = privParams.generatePublicKey()

        val kf = KeyFactory.getInstance("Ed25519", "BC")
        val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(privInfo.encoded))

        val pubInfo =
            SubjectPublicKeyInfo(
                AlgorithmIdentifier(EdECObjectIdentifiers.id_Ed25519),
                pubParams.encoded,
            )
        val pubKey = kf.generatePublic(X509EncodedKeySpec(pubInfo.encoded))

        return KeyPair(pubKey, privKey)
    }
}
