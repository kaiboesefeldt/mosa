package straightway.mosa.tls

import org.junit.jupiter.api.Test
import java.security.KeyStore
import java.security.cert.X509Certificate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevSelfSignedCertificateFactoryTest {
    @Test
    fun `creates key store with host in san`() {
        val factory = DevSelfSignedCertificateFactory()

        val result = factory.create("192.168.1.50")

        val certificate = result.certificate()
        assertEquals("CN=192.168.1.50", certificate.subjectX500Principal.name)
        val alternativeNames = certificate.subjectAlternativeNames.orEmpty()
        assertTrue(alternativeNames.any { (it[0] as Int) == 7 && it[1] == "192.168.1.50" })
    }

    @Test
    fun `provides private key entry`() {
        val factory = DevSelfSignedCertificateFactory()

        val result = factory.create("dev.pod")

        val entry = result.keyStore.getEntry(result.alias, KeyStore.PasswordProtection(result.password))
        assertTrue(entry is KeyStore.PrivateKeyEntry)
        val certificate = entry.certificate as X509Certificate
        assertEquals("CN=dev.pod", certificate.subjectX500Principal.name)
        val alternativeNames = certificate.subjectAlternativeNames.orEmpty()
        assertTrue(alternativeNames.any { (it[0] as Int) == 2 && it[1] == "dev.pod" })
    }

    private fun InMemoryKeyStore.certificate(): X509Certificate = keyStore.getCertificate(alias) as X509Certificate
}
