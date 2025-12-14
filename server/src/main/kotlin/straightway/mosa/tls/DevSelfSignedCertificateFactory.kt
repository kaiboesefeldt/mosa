package straightway.mosa.tls

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Security
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

data class InMemoryKeyStore(
    val keyStore: KeyStore,
    val alias: String,
    val password: CharArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InMemoryKeyStore

        if (keyStore != other.keyStore) return false
        if (alias != other.alias) return false
        if (!password.contentEquals(other.password)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyStore.hashCode()
        result = 31 * result + alias.hashCode()
        result = 31 * result + password.contentHashCode()
        return result
    }
}

class DevSelfSignedCertificateFactory(
    private val clock: Clock = Clock.systemUTC(),
) {
    fun create(host: String): InMemoryKeyStore {
        ensureProvider()
        val normalizedHost = host.removeSurrounding("[", "]")
        val keyPair = generateKeyPair()
        val certificate = createCertificate(normalizedHost, keyPair)
        return createKeyStore(keyPair, certificate)
    }

    private fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        return generator.generateKeyPair()
    }

    private fun createCertificate(
        host: String,
        keyPair: KeyPair,
    ): X509Certificate {
        val subject = X500Name("CN=$host")
        val now = clock.instant()
        val notBefore = Date.from(now.minus(5, ChronoUnit.MINUTES))
        val notAfter = Date.from(now.plus(30, ChronoUnit.DAYS))
        val serial = BigInteger.valueOf(now.toEpochMilli())

        val subjectAlternativeNames = GeneralNames(GeneralName(hostNameType(host), host))
        val publicKeyBytes = keyPair.public.encoded
        val subjectKeyIdentifier = SubjectKeyIdentifier(MessageDigest.getInstance("SHA-1").digest(publicKeyBytes))

        val certificateBuilder =
            JcaX509v3CertificateBuilder(
                subject,
                serial,
                notBefore,
                notAfter,
                subject,
                keyPair.public,
            ).addExtension(Extension.basicConstraints, true, BasicConstraints(false))
                .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment))
                .addExtension(Extension.subjectAlternativeName, false, subjectAlternativeNames)
                .addExtension(Extension.subjectKeyIdentifier, false, subjectKeyIdentifier)
                .addExtension(
                    Extension.extendedKeyUsage,
                    false,
                    ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth),
                )

        val signer =
            JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(PROVIDER_NAME)
                .build(keyPair.private)

        val holder = certificateBuilder.build(signer)

        return JcaX509CertificateConverter()
            .setProvider(PROVIDER_NAME)
            .getCertificate(holder)
    }

    private fun createKeyStore(
        keyPair: KeyPair,
        certificate: X509Certificate,
    ): InMemoryKeyStore {
        val password = randomPassword()
        val alias = "dev-${UUID.randomUUID()}"
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)
        keyStore.setKeyEntry(alias, keyPair.private, password, arrayOf(certificate))
        return InMemoryKeyStore(keyStore, alias, password)
    }

    private fun hostNameType(host: String): Int =
        if (host.any { it == ':' } || host.all { it.isDigit() || it == '.' }) GeneralName.iPAddress else GeneralName.dNSName

    private fun randomPassword(): CharArray = UUID.randomUUID().toString().toCharArray()

    companion object {
        private const val PROVIDER_NAME = "BC"

        private fun ensureProvider() {
            if (Security.getProvider(PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }
}
