package straightway.mosa.tls

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevSelfSignedCertificateFactoryTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `creates key store with host in san`() {
        val factory = DevSelfSignedCertificateFactory(clock = Clock.systemUTC(), certificateDirectory = tempDir)

        val result = factory.create("192.168.1.50")

        val certificate = result.certificate()
        assertEquals("CN=192.168.1.50", certificate.subjectX500Principal.name)
        val alternativeNames = certificate.subjectAlternativeNames.orEmpty()
        assertTrue(alternativeNames.any { (it[0] as Int) == 7 && it[1] == "192.168.1.50" })
    }

    @Test
    fun `provides private key entry`() {
        val factory = DevSelfSignedCertificateFactory(clock = Clock.systemUTC(), certificateDirectory = tempDir)

        val result = factory.create("dev.pod")

        val entry = result.keyStore.getEntry(result.alias, KeyStore.PasswordProtection(result.password))
        assertTrue(entry is KeyStore.PrivateKeyEntry)
        val certificate = entry.certificate as X509Certificate
        assertEquals("CN=dev.pod", certificate.subjectX500Principal.name)
        val alternativeNames = certificate.subjectAlternativeNames.orEmpty()
        assertTrue(alternativeNames.any { (it[0] as Int) == 2 && it[1] == "dev.pod" })
    }

    @Test
    fun `writes pem file when none exists`() {
        val factory = DevSelfSignedCertificateFactory(clock = Clock.systemUTC(), certificateDirectory = tempDir)

        factory.create("dev.pod.local")

        val pemPath = tempDir.resolve(".mosa-dev-cert-dev.pod.local")
        assertTrue(Files.exists(pemPath))
        val pemContent = Files.readString(pemPath)
        assertTrue(pemContent.contains("BEGIN PRIVATE KEY"))
        assertTrue(pemContent.contains("BEGIN CERTIFICATE"))
    }

    @Test
    fun `loads existing pem instead of creating new`() {
        val fixedClock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)
        val factory = DevSelfSignedCertificateFactory(clock = fixedClock, certificateDirectory = tempDir)

        val first = factory.create("dev.pod")
        val second = factory.create("dev.pod")

        assertEquals(first.certificate().serialNumber, second.certificate().serialNumber)
    }

    private fun InMemoryKeyStore.certificate(): X509Certificate = keyStore.getCertificate(alias) as X509Certificate
}
