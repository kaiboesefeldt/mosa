package straightway.mosa.client.legal

import straightway.mosa.legal.imprint.ImprintAddress
import straightway.mosa.legal.imprint.ImprintContactBlock
import straightway.mosa.legal.imprint.ImprintDocument
import straightway.mosa.legal.imprint.ImprintEditorialBlock
import straightway.mosa.legal.imprint.ImprintProviderBlock
import straightway.mosa.legal.imprint.ImprintRegisterBlock
import straightway.mosa.legal.imprint.ImprintSupervisionBlock
import straightway.mosa.legal.imprint.ImprintTaxBlock
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImprintHtmlRendererTest {
    private val renderer = ImprintHtmlRenderer()

    @Test
    fun `renders configured blocks in fixed order and markdown last`() {
        val html =
            renderer.render(
                ImprintDocument(
                    provider = ImprintProviderBlock(name = "Example Pod GmbH", representative = "Mira Example"),
                    address =
                        ImprintAddress(
                            street = "Main Street 1",
                            postalCode = "12345",
                            city = "Berlin",
                            country = "Deutschland",
                        ),
                    contact = ImprintContactBlock(email = "legal@example.invalid"),
                    register = ImprintRegisterBlock(number = "HRB 123"),
                    tax = ImprintTaxBlock(vatId = "DE123456789"),
                    supervision =
                        ImprintSupervisionBlock(
                            authority = "Media Authority",
                            address = ImprintAddress(city = "Bonn", country = "Deutschland"),
                        ),
                    editorial =
                        ImprintEditorialBlock(
                            responsiblePerson = "Mira Example",
                            address =
                                ImprintAddress(
                                    street = "Editorial Street 3",
                                    city = "Hamburg",
                                    country = "Deutschland",
                                ),
                        ),
                    additionalMarkdown = "# Additional\nExtra details",
                ),
            )

        assertContains(html, """legal-imprint__block--provider""")
        assertContains(html, """legal-imprint__block--address""")
        assertContains(html, """legal-imprint__block--contact""")
        assertContains(html, """legal-imprint__block--register""")
        assertContains(html, """legal-imprint__block--tax""")
        assertContains(html, """legal-imprint__block--supervision""")
        assertContains(html, """legal-imprint__block--editorial""")
        assertContains(html, """legal-imprint__block--additional""")
        assertContains(html, "<h1>Additional</h1>")
        assertTrue(html.indexOf("legal-imprint__block--provider") < html.indexOf("legal-imprint__block--address"))
        assertTrue(html.indexOf("legal-imprint__block--address") < html.indexOf("legal-imprint__block--contact"))
        assertTrue(html.indexOf("legal-imprint__block--contact") < html.indexOf("legal-imprint__block--register"))
        assertTrue(html.indexOf("legal-imprint__block--register") < html.indexOf("legal-imprint__block--tax"))
        assertTrue(html.indexOf("legal-imprint__block--tax") < html.indexOf("legal-imprint__block--supervision"))
        assertTrue(html.indexOf("legal-imprint__block--supervision") < html.indexOf("legal-imprint__block--editorial"))
        assertTrue(html.indexOf("legal-imprint__block--editorial") < html.indexOf("legal-imprint__block--additional"))
    }

    @Test
    fun `omits empty optional blocks and escapes plain text fields`() {
        val html =
            renderer.render(
                ImprintDocument(
                    provider = ImprintProviderBlock(name = """Example <Pod>"""),
                    address =
                        ImprintAddress(
                            street = "Main Street 1",
                            postalCode = "12345",
                            city = "Berlin",
                            country = "Deutschland",
                        ),
                    contact = ImprintContactBlock(email = "legal@example.invalid"),
                ),
            )

        assertContains(html, "Example &lt;Pod&gt;")
        assertFalse(html.contains("legal-imprint__block--register"))
        assertFalse(html.contains("legal-imprint__block--tax"))
        assertFalse(html.contains("legal-imprint__block--supervision"))
        assertFalse(html.contains("legal-imprint__block--editorial"))
        assertFalse(html.contains("legal-imprint__block--additional"))
        assertFalse(html.contains("Phone:"))
        assertFalse(html.contains("Website:"))
    }
}
