package straightway.mosa.legal.clearterms.render

import straightway.mosa.legal.clearterms.ClearTermsLocale
import straightway.mosa.legal.clearterms.ClearTermsNumberFormat
import straightway.mosa.legal.clearterms.ClearTermsNumberGrouping
import straightway.mosa.legal.clearterms.ClearTermsNumberOptions
import straightway.mosa.legal.clearterms.ClearTermsValidationException
import straightway.mosa.legal.clearterms.parse.ClearTermsScalarStringNormalizer

class ClearTermsDurationTextRenderer(
    private val numberRenderer: ClearTermsNumberRenderer = ClearTermsNumberRenderer(),
    private val scalarStringNormalizer: ClearTermsScalarStringNormalizer = ClearTermsScalarStringNormalizer(),
) {
    fun renderDurationValue(
        rawValue: String,
        locale: ClearTermsLocale,
    ): String {
        val normalized = scalarStringNormalizer.normalize(rawValue)
        if (normalized == "infinite") {
            return locale.duration.infinite
        }
        if (normalized.length < 2) {
            throw ClearTermsValidationException("Invalid duration value '$normalized'")
        }
        val unit = normalized.last()
        val quantity =
            normalized.dropLast(1).toDoubleOrNull() ?: throw ClearTermsValidationException("Invalid duration value '$normalized'")
        val quantityText =
            numberRenderer.format(
                value = quantity,
                options = ClearTermsNumberOptions(ClearTermsNumberFormat.Auto, ClearTermsNumberGrouping.OFF),
                locale = locale,
            )
        val unitText = locale.duration.labelFor(unit = unit, singular = kotlin.math.abs(quantity) == 1.0)
        return "$quantityText $unitText"
    }
}
