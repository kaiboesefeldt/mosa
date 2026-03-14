package straightway.mosa.client.legal

import straightway.mosa.legal.imprint.ImprintAddress
import straightway.mosa.legal.imprint.ImprintContactBlock
import straightway.mosa.legal.imprint.ImprintDocument
import straightway.mosa.legal.imprint.ImprintEditorialBlock
import straightway.mosa.legal.imprint.ImprintProviderBlock
import straightway.mosa.legal.imprint.ImprintRegisterBlock
import straightway.mosa.legal.imprint.ImprintSupervisionBlock
import straightway.mosa.legal.imprint.ImprintTaxBlock

class ImprintHtmlRenderer(
    private val markdownRenderer: MarkdownToHtmlRenderer = MarkdownToHtmlRenderer(),
) {
    fun render(document: ImprintDocument): String =
        buildString {
            renderProviderBlock(document.provider)?.let(::append)
            renderAddressBlock(document.address)?.let(::append)
            renderContactBlock(document.contact)?.let(::append)
            renderRegisterBlock(document.register)?.let(::append)
            renderTaxBlock(document.tax)?.let(::append)
            renderSupervisionBlock(document.supervision)?.let(::append)
            renderEditorialBlock(document.editorial)?.let(::append)
            renderAdditionalBlock(document.additionalMarkdown)?.let(::append)
        }

    private fun renderProviderBlock(block: ImprintProviderBlock): String? =
        renderBlock(
            cssModifier = "provider",
            title = null,
            lines =
                listOfNotNull(
                    block.name,
                    block.legalForm,
                    block.representative?.let { "Representative: $it" },
                ),
        )

    private fun renderAddressBlock(block: ImprintAddress): String? =
        renderBlock(
            cssModifier = "address",
            title = "Address",
            lines = addressLines(block),
        )

    private fun renderContactBlock(block: ImprintContactBlock): String? =
        renderBlock(
            cssModifier = "contact",
            title = "Contact",
            lines =
                listOfNotNull(
                    block.email?.let { "Email: $it" },
                    block.phone?.let { "Phone: $it" },
                    block.website?.let { "Website: $it" },
                ),
        )

    private fun renderRegisterBlock(block: ImprintRegisterBlock?): String? =
        renderBlock(
            cssModifier = "register",
            title = "Register",
            lines =
                block?.let {
                    listOfNotNull(
                        it.name,
                        it.court?.let { court -> "Court: $court" },
                        it.number?.let { number -> "Number: $number" },
                    )
                }.orEmpty(),
        )

    private fun renderTaxBlock(block: ImprintTaxBlock?): String? =
        renderBlock(
            cssModifier = "tax",
            title = "Tax",
            lines =
                block?.let {
                    listOfNotNull(
                        it.vatId?.let { vatId -> "VAT ID: $vatId" },
                        it.businessId?.let { businessId -> "Business ID: $businessId" },
                        it.other,
                    )
                }.orEmpty(),
        )

    private fun renderSupervisionBlock(block: ImprintSupervisionBlock?): String? =
        renderBlock(
            cssModifier = "supervision",
            title = "Supervision",
            lines =
                block?.let {
                    listOfNotNull(
                        it.authority,
                        *addressLines(it.address).toTypedArray(),
                        it.website?.let { website -> "Website: $website" },
                    )
                }.orEmpty(),
        )

    private fun renderEditorialBlock(block: ImprintEditorialBlock?): String? =
        renderBlock(
            cssModifier = "editorial",
            title = "Editorial",
            lines =
                block?.let {
                    listOfNotNull(
                        it.responsiblePerson,
                        *addressLines(it.address).toTypedArray(),
                    )
                }.orEmpty(),
        )

    private fun renderAdditionalBlock(markdown: String?): String? {
        val text = markdown?.trim()?.ifBlank { null } ?: return null
        return """
            <section class="legal-imprint__block legal-imprint__block--additional">
            <div class="legal-imprint__markdown">${markdownRenderer.render(text)}</div>
            </section>
        """.trimIndent()
    }

    private fun renderBlock(
        cssModifier: String,
        title: String?,
        lines: List<String>,
    ): String? {
        if (lines.isEmpty()) {
            return null
        }
        val titleHtml = title?.let { """<h3 class="legal-imprint__title">${escape(it)}</h3>""" }.orEmpty()
        val linesHtml =
            lines.joinToString(separator = "") { line ->
                """<p class="legal-imprint__line">${escape(line)}</p>"""
            }
        return """
            <section class="legal-imprint__block legal-imprint__block--$cssModifier">
            $titleHtml
            $linesHtml
            </section>
        """.trimIndent()
    }

    private fun addressLines(address: ImprintAddress?): List<String> {
        if (address == null) {
            return emptyList()
        }
        val cityLine =
            listOfNotNull(address.postalCode, address.city)
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" ")
        return listOfNotNull(
            address.street,
            cityLine,
            address.region,
            address.country,
        )
    }

    private fun escape(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}
