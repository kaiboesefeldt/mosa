package straightway.mosa.legal

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import straightway.mosa.legal.imprint.ImprintAddress
import straightway.mosa.legal.imprint.ImprintContactBlock
import straightway.mosa.legal.imprint.ImprintDocument
import straightway.mosa.legal.imprint.ImprintEditorialBlock
import straightway.mosa.legal.imprint.ImprintProviderBlock
import straightway.mosa.legal.imprint.ImprintRegisterBlock
import straightway.mosa.legal.imprint.ImprintSupervisionBlock
import straightway.mosa.legal.imprint.ImprintTaxBlock
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class ImprintService(
    private val path: Path,
    private val yamlMapper: ObjectMapper = ObjectMapper(YAMLFactory()),
) {
    private val documentCache: ImprintDocument by lazy { loadDocument() }

    fun validateStartup() {
        try {
            createTemplateIfMissing()
            document()
        } catch (exception: IllegalStateException) {
            logger.error(exception) { exception.message ?: "Failed to load imprint configuration." }
            throw exception
        }
    }

    fun document(): ImprintDocument = documentCache

    private fun createTemplateIfMissing() {
        try {
            Files.writeString(path, imprintTemplate, Charsets.UTF_8, StandardOpenOption.CREATE_NEW)
        } catch (_: FileAlreadyExistsException) {
            return
        }
    }

    private fun loadDocument(): ImprintDocument {
        val normalizedPath = path.toAbsolutePath().normalize()
        val root =
            try {
                if (!Files.exists(path)) {
                    throw IllegalStateException("Imprint config at '$normalizedPath' could not be read: file does not exist.")
                }
                Files.newBufferedReader(path).use(yamlMapper::readTree)
                    ?: throw IllegalStateException("Imprint config at '$normalizedPath' could not be read: file is empty.")
            } catch (exception: IllegalStateException) {
                throw exception
            } catch (exception: Exception) {
                throw IllegalStateException(
                    "Imprint config at '$normalizedPath' could not be read: ${exception.message}",
                    exception,
                )
            }
        if (!root.isObject) {
            throw IllegalStateException("Imprint config at '$normalizedPath' could not be read: root must be a YAML dictionary.")
        }
        val document =
            ImprintDocument(
                provider = parseProvider(root.path("provider")),
                address = parseAddress(root.path("address")),
                contact = parseContact(root.path("contact")),
                register = parseRegister(root.path("register")),
                tax = parseTax(root.path("tax")),
                supervision = parseSupervision(root.path("supervision")),
                editorial = parseEditorial(root.path("editorial")),
                additionalMarkdown = textValue(root.path("additional"), "markdown"),
            )
        val missingFields = document.requiredFieldNames().filter { document.fieldValue(it) == null }
        if (missingFields.isNotEmpty()) {
            throw IllegalStateException(
                "Imprint config at '$normalizedPath' is missing required field(s): ${missingFields.joinToString(", ")}.",
            )
        }
        return document
    }

    private fun parseProvider(node: JsonNode): ImprintProviderBlock =
        ImprintProviderBlock(
            name = textValue(node, "name"),
            legalForm = textValue(node, "legal_form"),
            representative = textValue(node, "representative"),
        )

    private fun parseAddress(node: JsonNode): ImprintAddress =
        ImprintAddress(
            street = textValue(node, "street"),
            postalCode = textValue(node, "postal_code"),
            city = textValue(node, "city"),
            region = textValue(node, "region"),
            country = textValue(node, "country"),
        )

    private fun parseContact(node: JsonNode): ImprintContactBlock =
        ImprintContactBlock(
            email = textValue(node, "email"),
            phone = textValue(node, "phone"),
            website = textValue(node, "website"),
        )

    private fun parseRegister(node: JsonNode): ImprintRegisterBlock? =
        blockOrNull(
            ImprintRegisterBlock(
                court = textValue(node, "court"),
                number = textValue(node, "number"),
                name = textValue(node, "name"),
            ),
        ) {
            listOf(it.court, it.number, it.name)
        }

    private fun parseTax(node: JsonNode): ImprintTaxBlock? =
        blockOrNull(
            ImprintTaxBlock(
                vatId = textValue(node, "vat_id"),
                businessId = textValue(node, "business_id"),
                other = textValue(node, "other"),
            ),
        ) {
            listOf(it.vatId, it.businessId, it.other)
        }

    private fun parseSupervision(node: JsonNode): ImprintSupervisionBlock? {
        val address = parseOptionalAddress(node.path("address"))
        return blockOrNull(
            ImprintSupervisionBlock(
                authority = textValue(node, "authority"),
                address = address,
                website = textValue(node, "website"),
            ),
        ) {
            listOf(
                it.authority,
                it.website,
                it.address?.street,
                it.address?.postalCode,
                it.address?.city,
                it.address?.region,
                it.address?.country,
            )
        }
    }

    private fun parseEditorial(node: JsonNode): ImprintEditorialBlock? {
        val address = parseOptionalAddress(node.path("address"))
        return blockOrNull(
            ImprintEditorialBlock(
                responsiblePerson = textValue(node, "responsible_person"),
                address = address,
            ),
        ) {
            listOf(
                it.responsiblePerson,
                it.address?.street,
                it.address?.postalCode,
                it.address?.city,
                it.address?.region,
                it.address?.country,
            )
        }
    }

    private fun parseOptionalAddress(node: JsonNode): ImprintAddress? =
        blockOrNull(parseAddress(node)) {
            listOf(it.street, it.postalCode, it.city, it.region, it.country)
        }

    private fun textValue(
        parent: JsonNode,
        fieldName: String,
    ): String? = normalizeText(parent.path(fieldName))

    private fun normalizeText(node: JsonNode?): String? {
        if (node == null || node.isMissingNode || node.isNull) {
            return null
        }
        return node.asText().trim().ifBlank { null }
    }

    private fun <T> blockOrNull(
        value: T,
        valuesProvider: (T) -> List<String?>,
    ): T? = value.takeIf { valuesProvider(it).any { item -> item != null } }
}

private fun ImprintDocument.requiredFieldNames(): List<String> =
    listOf(
        "provider.name",
        "address.street",
        "address.postal_code",
        "address.city",
        "address.country",
        "contact.email",
    )

private fun ImprintDocument.fieldValue(fieldName: String): String? =
    when (fieldName) {
        "provider.name" -> provider.name
        "address.street" -> address.street
        "address.postal_code" -> address.postalCode
        "address.city" -> address.city
        "address.country" -> address.country
        "contact.email" -> contact.email
        else -> null
    }

private val logger = KotlinLogging.logger {}

private val imprintTemplate =
    """
    # Replace null with the applicable text. Quote values containing YAML special characters.
    # Required fields must be filled in before the server can start.
    provider:
      name: null # Required: name of the provider
      legal_form: null # Optional: legal form of the provider
      representative: null # Optional: authorized representative
    address:
      street: null # Required: street address
      postal_code: null # Required: postal code
      city: null # Required: city
      region: null # Optional: state or region
      country: null # Required: country
    contact:
      email: null # Required: contact email address
      phone: null # Optional: telephone number
      website: null # Optional: website URL
    # Optional: complete the following fields when applicable.
    register:
      court: null # Register court
      number: null # Registration number
      name: null # Register name
    tax:
      vat_id: null # VAT identification number
      business_id: null # Business identification number
      other: null # Other tax information
    supervision:
      authority: null # Supervisory authority
      address:
        street: null
        postal_code: null
        city: null
        region: null
        country: null
      website: null # Supervisory authority website URL
    editorial:
      responsible_person: null # Person responsible for editorial content
      address:
        street: null
        postal_code: null
        city: null
        region: null
        country: null
    additional:
      markdown: null # Additional information in Markdown format
    """.trimIndent() + "\n"
