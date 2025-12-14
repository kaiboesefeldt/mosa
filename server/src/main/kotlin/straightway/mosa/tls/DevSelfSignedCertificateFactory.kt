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
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Base64
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
    private val certificateDirectory: Path = Path.of(""),
) {
    fun create(host: String): InMemoryKeyStore {
        ensureProvider()
        val normalizedHost = host.removeSurrounding("[", "]")
        val pemPath = certificateDirectory.resolve(pemFileName(normalizedHost))
        val (keyPair, certificate) =
            if (Files.exists(pemPath)) {
                loadFromPem(pemPath)
            } else {
                val generatedKeyPair = generateKeyPair()
                val generatedCertificate = createCertificate(normalizedHost, generatedKeyPair)
                writePem(pemPath, generatedKeyPair.private, generatedCertificate)
                Pair(generatedKeyPair, generatedCertificate)
            }
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
        val notAfter = MAX_EXPIRY
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

    private fun loadFromPem(pemPath: Path): Pair<KeyPair, X509Certificate> {
        val content = Files.readString(pemPath)
        val privateKey = parsePrivateKey(content)
        val certificate = parseCertificate(content)
        return Pair(KeyPair(certificate.publicKey, privateKey), certificate)
    }

    private fun parsePrivateKey(content: String): PrivateKey {
        val encoded = extractPemSection(content, "PRIVATE KEY")
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(encoded))
    }

    private fun parseCertificate(content: String): X509Certificate {
        val encoded = extractPemSection(content, "CERTIFICATE")
        val certificateFactory = CertificateFactory.getInstance("X.509")
        return certificateFactory.generateCertificate(ByteArrayInputStream(encoded)) as X509Certificate
    }

    private fun extractPemSection(
        content: String,
        section: String,
    ): ByteArray {
        val startMarker = "-----BEGIN $section-----"
        val endMarker = "-----END $section-----"
        val sectionContent =
            content
                .substringAfter(startMarker, "")
                .substringBefore(endMarker, "")
                .lines()
                .joinToString("") { it.trim() }
        return Base64.getMimeDecoder().decode(sectionContent)
    }

    private fun writePem(
        path: Path,
        privateKey: PrivateKey,
        certificate: X509Certificate,
    ) {
        val encoder = Base64.getMimeEncoder(64, "\n".toByteArray())
        val privateKeyBlock = encoder.encodeToString(privateKey.encoded)
        val certificateBlock = encoder.encodeToString(certificate.encoded)
        val content =
            buildString {
                appendLine("-----BEGIN PRIVATE KEY-----")
                appendLine(privateKeyBlock)
                appendLine("-----END PRIVATE KEY-----")
                appendLine("-----BEGIN CERTIFICATE-----")
                appendLine(certificateBlock)
                appendLine("-----END CERTIFICATE-----")
            }
        Files.writeString(path, content)
    }

    private fun pemFileName(host: String): String = "$CERTIFICATE_PREFIX-${sanitizedHost(host)}"

    private fun sanitizedHost(host: String): String = host.replace(":", "_")

    companion object {
        private const val PROVIDER_NAME = "BC"
        private const val CERTIFICATE_PREFIX = ".mosa-dev-cert"
        private val MAX_EXPIRY: Date =
            Date.from(LocalDate.of(9999, 12, 31).atStartOfDay(ZoneOffset.UTC).toInstant())

        private fun ensureProvider() {
            if (Security.getProvider(PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }
}
