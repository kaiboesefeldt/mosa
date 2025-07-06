package straightway.mosa.crypto

import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import straightway.mosa.Crypto
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.spec.NamedParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64

class CryptoEd25519 : Crypto {
    init {
        Security.addProvider(BouncyCastleProvider())
    }

    override fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("Ed25519", "BC")
        kpg.initialize(NamedParameterSpec("Ed25519"))
        return kpg.generateKeyPair()
    }

    override fun serializeKeyPair(keyPair: KeyPair): String {
        val privBytes = keyPair.private.encoded
        return Base64.UrlSafe
            .withPadding(Base64.PaddingOption.ABSENT)
            .encode(privBytes)
    }

    override fun deserializeKeyPair(base64: String): KeyPair {
        val privBytes =
            Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT)
                .decode(base64)

        return deserializePrivateKeyBytes(privBytes)
    }

    override fun exportPrivateKeyToPem(keyPair: KeyPair): String {
        val pemBody =
            Base64.Default
                .encode(keyPair.private.encoded)
                .chunked(64)
                .joinToString("\n")

        return buildString {
            appendLine("-----BEGIN PRIVATE KEY-----")
            appendLine(pemBody)
            appendLine("-----END PRIVATE KEY-----")
        }
    }

    override fun importPrivateKeyFromPem(pem: String): KeyPair {
        val rawB64 =
            pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s+".toRegex(), "")

        val der = Base64.Default.decode(rawB64)
        return deserializePrivateKeyBytes(der)
    }

    private fun deserializePrivateKeyBytes(privBytesPCKS8: ByteArray): KeyPair {
        val privInfo = PrivateKeyInfo.getInstance(privBytesPCKS8)

        val seed = (privInfo.parsePrivateKey().toASN1Primitive() as ASN1OctetString).octets
        val privParams = Ed25519PrivateKeyParameters(seed, 0)
        val pubParams = privParams.generatePublicKey()

        val kf = KeyFactory.getInstance("Ed25519", "BC")
        val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(privInfo.encoded))

        val spki = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(pubParams)
        val pubKey = kf.generatePublic(X509EncodedKeySpec(spki.encoded))

        return KeyPair(pubKey, privKey)
    }
}
