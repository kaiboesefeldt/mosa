package straightway.mosa.legal.clearterms.render

import straightway.mosa.legal.clearterms.locale.Locale
import straightway.mosa.legal.clearterms.validation.ValidationException
import straightway.mosa.legal.clearterms.value.NumberFormat
import straightway.mosa.legal.clearterms.value.NumberGrouping
import straightway.mosa.legal.clearterms.value.NumberOptions
import kotlin.math.pow

class NumberRenderer {
    fun format(
        value: Double,
        options: NumberOptions?,
        locale: Locale,
    ): String {
        val (format, grouping) =
            options?.run { this.format to this.grouping }
                ?: (NumberFormat.Auto to NumberGrouping.ON)
        return when (format) {
            is NumberFormat.Auto -> formatAuto(value, grouping, locale)
            is NumberFormat.Integer -> formatIntegerMode(value, grouping, locale)
            is NumberFormat.Decimal -> formatDecimalMode(value, format.scale, grouping, locale)
        }
    }

    private fun formatAuto(
        value: Double,
        grouping: NumberGrouping,
        locale: Locale,
    ): String {
        val asLong = value.toLong()
        return if (value == asLong.toDouble()) {
            formatInteger(asLong, grouping, locale)
        } else {
            formatDecimal(value, null, grouping, locale)
        }
    }

    private fun formatIntegerMode(
        value: Double,
        grouping: NumberGrouping,
        locale: Locale,
    ): String {
        val rounded = roundToNearestAwayFromZero(value).toLong()
        return formatInteger(rounded, grouping, locale)
    }

    private fun formatDecimalMode(
        value: Double,
        scale: Int,
        grouping: NumberGrouping,
        locale: Locale,
    ): String {
        val rounded = roundToScale(value, scale)
        return formatDecimal(rounded, scale, grouping, locale)
    }

    private fun formatInteger(
        value: Long,
        grouping: NumberGrouping,
        locale: Locale,
    ): String {
        val negative = value < 0
        val digits = kotlin.math.abs(value).toString()
        val grouped = if (grouping == NumberGrouping.ON) groupDigits(digits, locale.groupingSeparator) else digits
        return if (negative) "-$grouped" else grouped
    }

    private fun formatDecimal(
        value: Double,
        scale: Int?,
        grouping: NumberGrouping,
        locale: Locale,
    ): String {
        val negative = value < 0
        val absolute = kotlin.math.abs(value)
        val normalized = toPlainDecimalText(absolute)
        val text =
            if (scale == null) {
                normalized
            } else {
                val parts = normalized.split(".", limit = 2)
                val integerPart = parts[0]
                val fractionPart = parts.getOrNull(1).orEmpty()
                val padded = fractionPart.padEnd(scale, '0').take(scale)
                if (scale == 0) {
                    integerPart
                } else {
                    "$integerPart.$padded"
                }
            }
        val parts = text.split(".", limit = 2)
        val integerPart = parts[0]
        val fractionPart = parts.getOrNull(1)
        val groupedInteger =
            if (grouping == NumberGrouping.ON) {
                groupDigits(integerPart, locale.groupingSeparator)
            } else {
                integerPart
            }
        val withFraction =
            if (fractionPart.isNullOrEmpty()) {
                groupedInteger
            } else {
                groupedInteger + locale.decimalSeparator + fractionPart
            }
        return if (negative) "-$withFraction" else withFraction
    }

    private fun toPlainDecimalText(value: Double): String {
        if (!value.isFinite()) {
            throw ValidationException("Invalid number value")
        }
        val raw = value.toString()
        val exponentIndex = raw.indexOfAny(charArrayOf('e', 'E'))
        if (exponentIndex < 0) {
            return raw
        }
        val mantissa = raw.substring(0, exponentIndex)
        val exponentText = raw.substring(exponentIndex + 1)
        val exponent = exponentText.toInt()
        val whole = mantissa.substringBefore(".")
        val fraction = mantissa.substringAfter(".", "")
        val digits = whole + fraction
        val decimalIndex = whole.length + exponent

        val plain =
            when {
                decimalIndex <= 0 -> "0.${"0".repeat(-decimalIndex)}$digits"
                decimalIndex >= digits.length -> digits + "0".repeat(decimalIndex - digits.length)
                else -> digits.substring(0, decimalIndex) + "." + digits.substring(decimalIndex)
            }
        return plain
            .trimStart('0')
            .let { if (it.startsWith('.')) "0$it" else it }
            .let { if (it.contains('.')) it.trimEnd('0').trimEnd('.') else it }
    }

    private fun groupDigits(
        digits: String,
        separator: String,
    ): String {
        val builder = StringBuilder()
        var count = 0
        for (index in digits.length - 1 downTo 0) {
            builder.append(digits[index])
            count += 1
            if (count == 3 && index != 0) {
                builder.append(separator)
                count = 0
            }
        }
        return builder.reverse().toString()
    }

    private fun roundToNearestAwayFromZero(value: Double): Double =
        if (value >= 0) {
            kotlin.math.floor(value + 0.5)
        } else {
            kotlin.math.ceil(value - 0.5)
        }

    private fun roundToScale(
        value: Double,
        scale: Int,
    ): Double {
        val factor = 10.0.pow(scale)
        val scaled = value * factor
        val rounded = roundToNearestAwayFromZero(scaled)
        return rounded / factor
    }
}
