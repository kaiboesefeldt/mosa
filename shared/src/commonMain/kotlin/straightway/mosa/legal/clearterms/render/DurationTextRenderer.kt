package straightway.mosa.legal.clearterms.render

import straightway.mosa.legal.clearterms.locale.Locale
import straightway.mosa.legal.clearterms.parse.ScalarStringNormalizer
import straightway.mosa.legal.clearterms.validation.ValidationException
import straightway.mosa.legal.clearterms.value.NumberFormat
import straightway.mosa.legal.clearterms.value.NumberGrouping
import straightway.mosa.legal.clearterms.value.NumberOptions

class DurationTextRenderer(
    private val numberRenderer: NumberRenderer = NumberRenderer(),
    private val scalarStringNormalizer: ScalarStringNormalizer = ScalarStringNormalizer(),
) {
    fun renderDurationValue(
        rawValue: String,
        locale: Locale,
    ): String {
        val normalized = scalarStringNormalizer.normalize(rawValue)
        if (normalized == "infinite") {
            return locale.duration.infinite
        }
        if (normalized.length < 2) {
            throw ValidationException("Invalid duration value '$normalized'")
        }
        val unit = normalized.last()
        val quantity =
            normalized.dropLast(1).toDoubleOrNull() ?: throw ValidationException("Invalid duration value '$normalized'")
        val quantityText =
            numberRenderer.format(
                value = quantity,
                options = NumberOptions(NumberFormat.Auto, NumberGrouping.OFF),
                locale = locale,
            )
        val unitText = locale.duration.labelFor(unit = unit, singular = kotlin.math.abs(quantity) == 1.0)
        return "$quantityText $unitText"
    }
}
