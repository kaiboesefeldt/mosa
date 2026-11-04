package straightway.mosa.legal

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import straightway.mosa.legal.imprint.ImprintAddress
import straightway.mosa.legal.imprint.ImprintContactBlock
import straightway.mosa.legal.imprint.ImprintDocument
import straightway.mosa.legal.imprint.ImprintEditorialBlock
import straightway.mosa.legal.imprint.ImprintProviderBlock
import straightway.mosa.legal.imprint.ImprintRegisterBlock
import straightway.mosa.legal.imprint.ImprintSupervisionBlock
import straightway.mosa.legal.imprint.ImprintTaxBlock
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ImprintServiceTest {
    @Test
    fun `startup creates an incomplete imprint template when missing`() {
        val path = Files.createTempDirectory("imprint-template").resolve("mosa_imprint.yaml")

        val exception = assertThrows<IllegalStateException> { ImprintService(path).validateStartup() }

        assertContains(exception.message.orEmpty(), "provider.name")
        val template = Files.readString(path)
        assertContains(template, "postal_code: null")
        assertContains(template, "email: null")
        assertContains(template, "# Required")
        assertContains(template, "# Optional")
        assertFalse(template.contains("\"\""))
    }

    @Test
    fun `startup preserves an existing imprint file`() {
        val content = "provider:\n  name: Existing\n"
        val path = writeImprintYaml(content)

        assertThrows<IllegalStateException> { ImprintService(path).validateStartup() }

        assertEquals(content, Files.readString(path))
    }

    @Test
    fun `loads valid imprint configuration with all supported blocks`() {
        val path =
            writeImprintYaml(
                """
                provider:
                  name: "Example Pod GmbH"
                  legal_form: "GmbH"
                  representative: "Mira Example"
                address:
                  street: "Main Street 1"
                  postal_code: "12345"
                  city: "Berlin"
                  region: "Berlin"
                  country: "Deutschland"
                contact:
                  email: "hello@example.invalid"
                  phone: "+49 30 123456"
                  website: "https://example.invalid"
                register:
                  court: "Amtsgericht Berlin"
                  number: "HRB 123"
                  name: "Handelsregister"
                tax:
                  vat_id: "DE123456789"
                  business_id: "BUS-42"
                  other: "Tax note"
                supervision:
                  authority: "Media Authority"
                  address:
                    street: "Authority Street 2"
                    postal_code: "54321"
                    city: "Bonn"
                    country: "Deutschland"
                  website: "https://authority.invalid"
                editorial:
                  responsible_person: "Mira Example"
                  address:
                    street: "Editorial Street 3"
                    postal_code: "99999"
                    city: "Hamburg"
                    country: "Deutschland"
                additional:
                  markdown: |
                    Additional line 1
                    Additional line 2
                """.trimIndent(),
            )

        val document = ImprintService(path = path).document()

        assertEquals(
            ImprintDocument(
                provider =
                    ImprintProviderBlock(
                        name = "Example Pod GmbH",
                        legalForm = "GmbH",
                        representative = "Mira Example",
                    ),
                address =
                    ImprintAddress(
                        street = "Main Street 1",
                        postalCode = "12345",
                        city = "Berlin",
                        region = "Berlin",
                        country = "Deutschland",
                    ),
                contact =
                    ImprintContactBlock(
                        email = "hello@example.invalid",
                        phone = "+49 30 123456",
                        website = "https://example.invalid",
                    ),
                register =
                    ImprintRegisterBlock(
                        court = "Amtsgericht Berlin",
                        number = "HRB 123",
                        name = "Handelsregister",
                    ),
                tax =
                    ImprintTaxBlock(
                        vatId = "DE123456789",
                        businessId = "BUS-42",
                        other = "Tax note",
                    ),
                supervision =
                    ImprintSupervisionBlock(
                        authority = "Media Authority",
                        address =
                            ImprintAddress(
                                street = "Authority Street 2",
                                postalCode = "54321",
                                city = "Bonn",
                                country = "Deutschland",
                            ),
                        website = "https://authority.invalid",
                    ),
                editorial =
                    ImprintEditorialBlock(
                        responsiblePerson = "Mira Example",
                        address =
                            ImprintAddress(
                                street = "Editorial Street 3",
                                postalCode = "99999",
                                city = "Hamburg",
                                country = "Deutschland",
                            ),
                    ),
                additionalMarkdown = "Additional line 1\nAdditional line 2",
            ),
            document,
        )
    }

    @Test
    fun `allows missing optional blocks and null optional values`() {
        val path =
            writeImprintYaml(
                """
                provider:
                  name: "Example Pod"
                  legal_form: null
                  representative: null
                address:
                  street: "Main Street 1"
                  postal_code: "12345"
                  city: "Berlin"
                  region: null
                  country: "Deutschland"
                contact:
                  email: "hello@example.invalid"
                  phone: null
                  website: null
                """.trimIndent(),
            )

        val document = ImprintService(path = path).document()

        assertEquals("Example Pod", document.provider.name)
        assertNull(document.provider.legalForm)
        assertNull(document.register)
        assertNull(document.tax)
        assertNull(document.supervision)
        assertNull(document.editorial)
        assertNull(document.additionalMarkdown)
    }

    @Test
    fun `fails when required imprint field is missing`() {
        val path =
            writeImprintYaml(
                """
                provider:
                  name: "Example Pod"
                address:
                  street: "Main Street 1"
                  postal_code: "12345"
                  country: "Deutschland"
                contact:
                  email: "hello@example.invalid"
                """.trimIndent(),
            )

        val exception =
            assertThrows<IllegalStateException> {
                ImprintService(path = path).document()
            }

        assertContains(exception.message ?: "", path.toAbsolutePath().normalize().toString())
        assertContains(exception.message ?: "", "address.city")
    }

    @Test
    fun `fails when imprint file is missing`() {
        val exception =
            assertThrows<IllegalStateException> {
                ImprintService(path = Path.of("/tmp/does-not-exist-imprint.yaml")).document()
            }

        assertContains(exception.message ?: "", "file does not exist")
    }

    @Test
    fun `fails when imprint yaml is not readable`() {
        val path =
            writeImprintYaml(
                """
                provider: [
                """.trimIndent(),
            )

        val exception =
            assertThrows<IllegalStateException> {
                ImprintService(path = path).document()
            }

        assertContains(exception.message ?: "", "could not be read")
    }

    @Test
    fun `validate startup logs clear error before rethrowing`() {
        val path =
            writeImprintYaml(
                """
                provider:
                  name: "Example Pod"
                address:
                  street: "Main Street 1"
                  postal_code: "12345"
                  city: "Berlin"
                  country: "Deutschland"
                contact:
                  email: null
                """.trimIndent(),
            )
        val logger = LoggerFactory.getLogger(ImprintService::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)

        try {
            val exception =
                assertThrows<IllegalStateException> {
                    ImprintService(path = path).validateStartup()
                }

            assertContains(exception.message ?: "", "contact.email")
            val loggedMessage = appender.list.joinToString("\n") { it.formattedMessage }
            assertContains(loggedMessage, "contact.email")
            assertContains(loggedMessage, path.toAbsolutePath().normalize().toString())
        } finally {
            logger.detachAppender(appender)
        }
    }

    private fun writeImprintYaml(content: String) = Files.createTempFile("imprint", ".yaml").also { Files.writeString(it, content) }
}
