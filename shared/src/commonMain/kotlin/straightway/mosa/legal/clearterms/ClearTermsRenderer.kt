package straightway.mosa.legal.clearterms

import kotlin.math.pow

class ClearTermsRenderer(
    private val parser: ClearTermsJsonParser,
    private val hashProvider: ClearTermsHashProvider,
    private val textEncoder: ClearTermsTextEncoder,
    private val canonicalizer: ClearTermsCanonicalJson = ClearTermsCanonicalJson(),
    private val placeholderParser: ClearTermsPlaceholderParser = ClearTermsPlaceholderParser(),
    private val reader: ClearTermsDocumentReader = ClearTermsDocumentReader(),
) {
    suspend fun renderAll(
        baselineJson: String,
        defaultJson: String,
        deviationJson: String,
    ): ClearTermsRenderResult {
        val baselineNode = parser.parse(baselineJson)
        val defaultNode = parser.parse(defaultJson)
        val deviationNode = parser.parse(deviationJson)

        val baselineHash = hashFor(baselineNode)
        val defaultHash = hashFor(defaultNode)

        val baseline = reader.readBaseline(baselineNode)
        val defaultProfile = reader.readDefault(defaultNode)
        val deviationProfile = reader.readDeviation(deviationNode)

        validateReference(defaultProfile.baseline, baseline.id, baseline.version, baselineHash)
        validateReference(deviationProfile.default, defaultProfile.id, defaultProfile.version, defaultHash)

        validateBaselineStructure(baseline)

        val baselinePlaceholders = placeholdersByKey(baseline.textsByLanguage)

        val defaultValues = validateAndCoerceValues(defaultProfile.values, baselinePlaceholders, requireAll = true)

        val effectiveTexts = applyTextOverrides(baseline, deviationProfile.overrides.texts)
        val effectivePlaceholders = placeholdersByKey(effectiveTexts)

        val deviationValues = validateAndCoerceValues(deviationProfile.overrides.values, effectivePlaceholders, requireAll = false)

        val effectiveValues = defaultValues + deviationValues

        val renderedByLanguage =
            effectiveTexts.mapValues { (language, blocks) ->
                renderBlocks(blocks, effectiveValues, language)
            }

        return ClearTermsRenderResult(renderedByLanguage)
    }

    suspend fun renderLanguage(
        baselineJson: String,
        defaultJson: String,
        deviationJson: String,
        languageTag: String,
    ): String {
        val result = renderAll(baselineJson, defaultJson, deviationJson)
        return result.textsByLanguage[languageTag]
            ?: throw ClearTermsValidationException("Language '$languageTag' not available")
    }

    private suspend fun hashFor(node: ClearTermsJsonNode.ObjectNode): String {
        val canonical = canonicalizer.canonicalize(node)
        val digest = hashProvider.sha256(textEncoder.encode(canonical))
        return "sha256:${digest.toHexLower()}"
    }

    private fun validateReference(
        reference: ClearTermsReference,
        id: String,
        version: String,
        hash: String,
    ) {
        if (reference.id != id) {
            throw ClearTermsValidationException("Reference id mismatch")
        }
        if (reference.version != version) {
            throw ClearTermsValidationException("Reference version mismatch")
        }
        if (reference.hash.lowercase() != hash.lowercase()) {
            throw ClearTermsValidationException("Reference hash mismatch")
        }
    }

    private fun validateBaselineStructure(baseline: ClearTermsBaseline) {
        val languages = baseline.textsByLanguage
        val referenceBlocks = languages.values.first()
        val referenceIds = referenceBlocks.map { it.id }
        referenceIds.requireUnique("Baseline block ids must be unique")
        languages.forEach { (language, blocks) ->
            val ids = blocks.map { it.id }
            ids.requireUnique("Baseline block ids must be unique for '$language'")
            if (ids != referenceIds) {
                throw ClearTermsValidationException("Baseline block alignment mismatch")
            }
        }
    }

    private fun applyTextOverrides(
        baseline: ClearTermsBaseline,
        overrides: Map<String, ClearTermsDeviationProfile.TextOverrides>?,
    ): Map<String, List<ClearTermsTextBlock>> {
        if (overrides == null) {
            return baseline.textsByLanguage
        }
        if (overrides.keys != baseline.textsByLanguage.keys) {
            throw ClearTermsValidationException("Deviation texts must cover all baseline languages")
        }
        val baselineIds =
            baseline.textsByLanguage.values
                .first()
                .map { it.id }
                .toSet()
        val replaceKeys =
            overrides.values
                .first()
                .replace.keys
        overrides.values.forEach { languageOverrides ->
            if (languageOverrides.replace.keys != replaceKeys) {
                throw ClearTermsValidationException("Replace block ids must match across languages")
            }
            val missing = languageOverrides.replace.keys.firstOrNull { it !in baselineIds }
            if (missing != null) {
                throw ClearTermsValidationException("Replace block id '$missing' does not exist")
            }
        }
        val appendReference = overrides.values.first().append
        val appendIds = appendReference.map { it.id }
        appendIds.requireUnique("Append block ids must be unique")
        if (appendIds.any { it in baselineIds }) {
            throw ClearTermsValidationException("Append block ids must not collide with baseline")
        }
        overrides.values.forEach { languageOverrides ->
            if (languageOverrides.append.map { it.id } != appendIds) {
                throw ClearTermsValidationException("Append block ids must align across languages")
            }
        }
        overrides.values.forEach { languageOverrides ->
            languageOverrides.replace.values.forEach { text ->
                ensureNoFallbacks(text)
            }
            languageOverrides.append.forEach { block ->
                ensureNoFallbacks(block.text)
            }
        }
        return baseline.textsByLanguage.mapValues { (language, blocks) ->
            val languageOverrides = overrides.getValue(language)
            val replaced =
                blocks.map { block ->
                    val replacement = languageOverrides.replace[block.id]
                    if (replacement == null) {
                        block
                    } else {
                        ClearTermsTextBlock(block.id, replacement)
                    }
                }
            replaced + languageOverrides.append
        }
    }

    private fun placeholdersByKey(textsByLanguage: Map<String, List<ClearTermsTextBlock>>): Map<String, ClearTermsPlaceholderType> {
        val placeholders = linkedMapOf<String, ClearTermsPlaceholderType>()
        textsByLanguage.values.flatten().forEach { block ->
            val segments = placeholderParser.parse(block.text)
            segments.filterIsInstance<ClearTermsPlaceholderParser.Segment.Placeholder>().forEach { segment ->
                val key = segment.placeholder.key
                val type = segment.placeholder.type
                val existing = placeholders[key]
                if (existing != null && existing != type) {
                    throw ClearTermsValidationException("Placeholder type mismatch for '$key'")
                }
                placeholders[key] = type
            }
        }
        return placeholders
    }

    private fun ensureNoFallbacks(text: String) {
        val segments = placeholderParser.parse(text)
        segments.filterIsInstance<ClearTermsPlaceholderParser.Segment.Placeholder>().forEach { segment ->
            if (segment.placeholder.fallback != null) {
                throw ClearTermsValidationException("Fallbacks are not allowed in deviation text")
            }
        }
    }

    private fun validateAndCoerceValues(
        rawValues: Map<String, ClearTermsJsonNode.ScalarNode>,
        types: Map<String, ClearTermsPlaceholderType>,
        requireAll: Boolean,
    ): Map<String, ClearTermsValue> {
        val missing = types.keys.firstOrNull { requireAll && it !in rawValues.keys }
        if (missing != null) {
            throw ClearTermsValidationException("Missing value for '$missing'")
        }
        return rawValues.mapValues { (key, raw) ->
            val type =
                types[key]
                    ?: throw ClearTermsValidationException("Unknown placeholder '$key'")
            val value =
                when (raw) {
                    is ClearTermsJsonNode.ScalarNode.StringNode -> raw.value
                    is ClearTermsJsonNode.ScalarNode.NumberNode -> raw.value
                    ClearTermsJsonNode.ScalarNode.NullNode -> null
                }
            validateValue(type, value)
            ClearTermsValue(type, value)
        }
    }

    private fun validateValue(
        type: ClearTermsPlaceholderType,
        value: Any?,
    ) {
        when (type) {
            ClearTermsPlaceholderType.STRING -> validateStringValue(value)
            ClearTermsPlaceholderType.NUMBER -> validateNumberValue(value)
            ClearTermsPlaceholderType.DURATION -> validateDurationValue(value)
            ClearTermsPlaceholderType.TIME -> validateTimeValue(value)
            ClearTermsPlaceholderType.WEEKDAY -> validateWeekdayValue(value)
        }
    }

    private fun validateStringValue(value: Any?) {
        if (value !is String) {
            throw ClearTermsValidationException("String value must be provided")
        }
    }

    private fun validateNumberValue(value: Any?) {
        if (value != null && value !is Double) {
            throw ClearTermsValidationException("Number value must be numeric")
        }
    }

    private fun validateDurationValue(value: Any?) {
        if (value == null) {
            return
        }
        val text =
            value as? String
                ?: throw ClearTermsValidationException("Duration value must be string")
        val normalized = normalizeScalarString(text)
        if (!isValidDuration(normalized)) {
            throw ClearTermsValidationException("Invalid duration value '$normalized'")
        }
    }

    private fun validateTimeValue(value: Any?) {
        if (value == null) {
            return
        }
        val text =
            value as? String
                ?: throw ClearTermsValidationException("Time value must be string")
        val normalized = normalizeScalarString(text)
        parseTimeValue(normalized)
    }

    private fun validateWeekdayValue(value: Any?) {
        if (value == null) {
            return
        }
        val text =
            value as? String
                ?: throw ClearTermsValidationException("Weekday value must be string")
        val normalized = normalizeScalarString(text).lowercase()
        if (!weekdayRegex.matches(normalized)) {
            throw ClearTermsValidationException("Invalid weekday value '$normalized'")
        }
    }

    private fun renderBlocks(
        blocks: List<ClearTermsTextBlock>,
        values: Map<String, ClearTermsValue>,
        languageTag: String,
    ): String {
        val rendered = blocks.joinToString("") { block -> renderText(block.text, values, languageTag) }
        if (containsUnresolvedPlaceholder(rendered)) {
            throw ClearTermsValidationException("Rendered text contains unresolved placeholders")
        }
        return rendered
    }

    private fun renderText(
        text: String,
        values: Map<String, ClearTermsValue>,
        languageTag: String,
    ): String {
        val segments = placeholderParser.parse(text)
        val result = StringBuilder()
        segments.forEach { segment ->
            when (segment) {
                is ClearTermsPlaceholderParser.Segment.Literal -> result.append(segment.text)
                is ClearTermsPlaceholderParser.Segment.Placeholder -> {
                    val placeholder = segment.placeholder
                    val value =
                        values[placeholder.key]
                            ?: throw ClearTermsValidationException("Missing value for '${placeholder.key}'")
                    val rendered = renderValue(value, placeholder, languageTag)
                    result.append(rendered)
                }
            }
        }
        return result.toString()
    }

    private fun containsUnresolvedPlaceholder(text: String): Boolean {
        var index = 0
        while (index < text.length - 1) {
            if (text.startsWith("{{", index)) {
                val endIndex = text.indexOf("}}", index + 2)
                if (endIndex < 0) {
                    throw ClearTermsValidationException("Rendered text contains unterminated placeholder")
                }
                val content = text.substring(index + 2, endIndex)
                if (matchesPlaceholderSyntax(content)) {
                    return true
                }
                index = endIndex + 2
            } else {
                index += 1
            }
        }
        return false
    }

    private fun matchesPlaceholderSyntax(content: String): Boolean {
        val parts = content.split("??", limit = 2)
        val main = parts[0]
        val typeAndRest = main.split(":", limit = 2)
        if (typeAndRest.size != 2) {
            return false
        }
        val type =
            when (typeAndRest[0]) {
                "string", "number", "duration", "time", "weekday" -> true
                else -> false
            }
        if (!type) {
            return false
        }
        val keyAndOptions = typeAndRest[1]
        val key = keyAndOptions.substringBefore("?")
        if (key.isBlank() || key.any { it.isWhitespace() }) {
            return false
        }
        if (keyAndOptions.contains("?")) {
            val options = keyAndOptions.substringAfter("?")
            if (options.isBlank()) {
                return false
            }
        }
        return true
    }

    private fun renderValue(
        value: ClearTermsValue,
        placeholder: ClearTermsPlaceholder,
        languageTag: String,
    ): String {
        val raw = value.value
        if (raw == null) {
            return placeholder.fallback
                ?: throw ClearTermsValidationException("Missing fallback for '${placeholder.key}'")
        }
        val locale = resolveLocale(languageTag)
        return when (value.type) {
            ClearTermsPlaceholderType.STRING -> raw as String
            ClearTermsPlaceholderType.NUMBER -> formatNumber(raw as Double, placeholder.numberOptions, locale)
            ClearTermsPlaceholderType.DURATION -> raw as String
            ClearTermsPlaceholderType.TIME -> renderTimeValue(raw as String, locale)
            ClearTermsPlaceholderType.WEEKDAY -> renderWeekday(raw as String, locale)
        }
    }

    private fun formatNumber(
        value: Double,
        options: ClearTermsNumberOptions?,
        locale: ClearTermsLocale,
    ): String {
        val format = options?.format ?: ClearTermsNumberFormat.Auto
        val grouping = options?.grouping ?: ClearTermsNumberGrouping.ON
        return when (format) {
            is ClearTermsNumberFormat.Auto -> formatNumberAuto(value, grouping, locale)
            is ClearTermsNumberFormat.Integer -> formatNumberInteger(value, grouping, locale)
            is ClearTermsNumberFormat.Decimal -> formatNumberDecimal(value, format.scale, grouping, locale)
        }
    }

    private fun formatNumberAuto(
        value: Double,
        grouping: ClearTermsNumberGrouping,
        locale: ClearTermsLocale,
    ): String {
        val asLong = value.toLong()
        return if (value == asLong.toDouble()) {
            formatInteger(asLong, grouping, locale)
        } else {
            formatDecimal(value, null, grouping, locale)
        }
    }

    private fun formatNumberInteger(
        value: Double,
        grouping: ClearTermsNumberGrouping,
        locale: ClearTermsLocale,
    ): String {
        val rounded = roundToNearestAwayFromZero(value).toLong()
        return formatInteger(rounded, grouping, locale)
    }

    private fun formatNumberDecimal(
        value: Double,
        scale: Int,
        grouping: ClearTermsNumberGrouping,
        locale: ClearTermsLocale,
    ): String {
        val rounded = roundToScale(value, scale)
        return formatDecimal(rounded, scale, grouping, locale)
    }

    private fun formatInteger(
        value: Long,
        grouping: ClearTermsNumberGrouping,
        locale: ClearTermsLocale,
    ): String {
        val negative = value < 0
        val digits = kotlin.math.abs(value).toString()
        val grouped = if (grouping == ClearTermsNumberGrouping.ON) groupDigits(digits, locale.groupingSeparator) else digits
        return if (negative) "-$grouped" else grouped
    }

    private fun formatDecimal(
        value: Double,
        scale: Int?,
        grouping: ClearTermsNumberGrouping,
        locale: ClearTermsLocale,
    ): String {
        val negative = value < 0
        val absolute = kotlin.math.abs(value)
        val text =
            if (scale == null) {
                absolute.toString()
            } else {
                val normalized = absolute.toString()
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
            if (grouping ==
                ClearTermsNumberGrouping.ON
            ) {
                groupDigits(integerPart, locale.groupingSeparator)
            } else {
                integerPart
            }
        val withFraction =
            if (fractionPart == null || fractionPart.isEmpty()) {
                groupedInteger
            } else {
                groupedInteger + locale.decimalSeparator + fractionPart
            }
        return if (negative) "-$withFraction" else withFraction
    }

    private fun groupDigits(
        digits: String,
        separator: Char,
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

    private fun renderWeekday(
        value: String,
        locale: ClearTermsLocale,
    ): String {
        val normalized = normalizeScalarString(value).lowercase()
        return locale.weekdays[normalized] ?: normalized
    }

    private fun renderTimeValue(
        value: String,
        locale: ClearTermsLocale,
    ): String {
        val normalized = normalizeScalarString(value)
        val parts = parseTimeValue(normalized)
        val hasTime = parts.time != null
        val dateText = renderDatePattern(parts, locale)
        return if (hasTime) {
            "$dateText ${locale.atWord} ${parts.time}"
        } else {
            dateText
        }
    }

    private fun renderDatePattern(
        parts: ClearTermsTimeParts,
        locale: ClearTermsLocale,
    ): String {
        val year = parts.year
        val month = parts.month
        val day = parts.day
        val overflow = parts.isOverflow
        return when {
            year != null && month != null && day != null && !overflow ->
                locale.onFullDate(locale.monthName(month), day, year)
            year != null && month != null && day == null ->
                locale.inMonthYear(locale.monthName(month), year)
            year != null && month == null && day != null && !overflow ->
                locale.onDayOfEveryMonthInYear(day, year)
            year != null && month == null && day == null ->
                locale.inYear(year)
            year == null && month != null && day != null && !overflow ->
                locale.onMonthDayEveryYear(locale.monthName(month), day)
            year == null && month != null && day == null ->
                locale.everyDayInMonth(locale.monthName(month))
            year == null && month == null && day != null && !overflow ->
                locale.onDayOfEveryMonth(day)
            year == null && month == null && day == null ->
                locale.everyDay()
            overflow -> renderOverflowDate(parts, locale)
            else -> throw ClearTermsValidationException("Unsupported time pattern")
        }
    }

    private fun renderOverflowDate(
        parts: ClearTermsTimeParts,
        locale: ClearTermsLocale,
    ): String {
        val year = parts.year
        val month = parts.month
        return when {
            year != null && month != null -> locale.onLastDayOfMonth(locale.monthName(month), year)
            year == null && month != null -> locale.onLastDayOfMonthEveryYear(locale.monthName(month))
            year != null && month == null -> locale.onLastDayOfEveryMonthInYear(year)
            else -> locale.onLastDayOfEveryMonth()
        }
    }

    private fun resolveLocale(languageTag: String): ClearTermsLocale {
        val primary = languageTag.substringBefore('-').substringBefore('_').lowercase()
        return locales[primary] ?: locales.getValue("en")
    }

    private fun parseTimeValue(value: String): ClearTermsTimeParts {
        if (!timeRegex.matches(value)) {
            throw ClearTermsValidationException("Invalid time value '$value'")
        }
        val datePart = value.substringBefore("T")
        val timePart = value.substringAfter("T", "")
        val dateSegments = datePart.split("-", limit = 3)
        if (dateSegments.size != 3) {
            throw ClearTermsValidationException("Invalid time value '$value'")
        }
        val year = parseYear(dateSegments[0], value)
        val month = parseMonth(dateSegments[1], value)
        val day = parseDay(dateSegments[2], value)
        val time = if (timePart.isEmpty()) null else parseTimeOfDay(timePart, value)
        val overflow = isOverflowDay(year, month, day)
        return ClearTermsTimeParts(year, month, day, time, overflow)
    }

    private fun parseYear(
        value: String,
        raw: String,
    ): Int? {
        if (value == "*") {
            return null
        }
        return value.toIntOrNull()
            ?: throw ClearTermsValidationException("Invalid time value '$raw'")
    }

    private fun parseMonth(
        value: String,
        raw: String,
    ): Int? {
        if (value == "*") {
            return null
        }
        val month =
            value.toIntOrNull()
                ?: throw ClearTermsValidationException("Invalid time value '$raw'")
        if (month < 1 || month > 12) {
            throw ClearTermsValidationException("Invalid time value '$raw'")
        }
        return month
    }

    private fun parseDay(
        value: String,
        raw: String,
    ): Int? {
        if (value == "*") {
            return null
        }
        val day =
            value.toIntOrNull()
                ?: throw ClearTermsValidationException("Invalid time value '$raw'")
        if (day < 1 || day > 31) {
            throw ClearTermsValidationException("Invalid time value '$raw'")
        }
        return day
    }

    private fun parseTimeOfDay(
        value: String,
        raw: String,
    ): String {
        val zoneIndex = findZoneIndex(value)
        if (zoneIndex < 0) {
            throw ClearTermsValidationException("Invalid time value '$raw'")
        }
        val timePart = value.substring(0, zoneIndex)
        val zonePart = value.substring(zoneIndex)
        val segments = timePart.split(":")
        if (segments.size !in setOf(2, 3)) {
            throw ClearTermsValidationException("Invalid time value '$raw'")
        }
        val hour = segments[0].toIntOrNull()
        val minute = segments[1].toIntOrNull()
        val second = segments.getOrNull(2)?.toIntOrNull()
        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
            throw ClearTermsValidationException("Invalid time value '$raw'")
        }
        if (second != null && second !in 0..59) {
            throw ClearTermsValidationException("Invalid time value '$raw'")
        }
        if (!zoneRegex.matches(zonePart)) {
            throw ClearTermsValidationException("Invalid time value '$raw'")
        }
        return value
    }

    private fun findZoneIndex(value: String): Int {
        if (value.endsWith("Z")) {
            return value.length - 1
        }
        val plusIndex = value.lastIndexOf('+')
        val minusIndex = value.lastIndexOf('-')
        val index = maxOf(plusIndex, minusIndex)
        return index
    }

    private fun isOverflowDay(
        year: Int?,
        month: Int?,
        day: Int?,
    ): Boolean {
        if (day == null) {
            return false
        }
        if (month == null) {
            return day == 31
        }
        val maxDay = maxDayOfMonth(year, month)
        return day > maxDay
    }

    private fun maxDayOfMonth(
        year: Int?,
        month: Int,
    ): Int =
        when (month) {
            2 -> {
                if (year != null && isLeapYear(year)) {
                    29
                } else if (year == null) {
                    29
                } else {
                    28
                }
            }
            4, 6, 9, 11 -> 30
            else -> 31
        }

    private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    private fun normalizeScalarString(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.length >= 2 && trimmed.startsWith('\"') && trimmed.endsWith('\"')) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }

    private fun isValidDuration(value: String): Boolean {
        if (value == "infinite") {
            return true
        }
        if (value.length < 2) {
            return false
        }
        val unit = value.last()
        if (unit !in setOf('s', 'm', 'h', 'd', 'w', 'y')) {
            return false
        }
        val number = value.dropLast(1)
        return number.toDoubleOrNull() != null
    }

    private fun ByteArray.toHexLower(): String =
        joinToString("") { byte ->
            val value = byte.toInt() and 0xff
            value.toString(16).padStart(2, '0')
        }

    private fun List<String>.requireUnique(message: String) {
        val duplicates = groupBy { it }.filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            throw ClearTermsValidationException(message)
        }
    }

    private data class ClearTermsTimeParts(
        val year: Int?,
        val month: Int?,
        val day: Int?,
        val time: String?,
        val isOverflow: Boolean,
    )

    private data class ClearTermsLocale(
        val language: String,
        val months: List<String>,
        val weekdays: Map<String, String>,
        val decimalSeparator: Char,
        val groupingSeparator: Char,
        val atWord: String,
        val onFullDate: (String, Int, Int) -> String,
        val inMonthYear: (String, Int) -> String,
        val onDayOfEveryMonthInYear: (Int, Int) -> String,
        val inYear: (Int) -> String,
        val onMonthDayEveryYear: (String, Int) -> String,
        val everyDayInMonth: (String) -> String,
        val onDayOfEveryMonth: (Int) -> String,
        val everyDay: () -> String,
        val onLastDayOfMonth: (String, Int) -> String,
        val onLastDayOfMonthEveryYear: (String) -> String,
        val onLastDayOfEveryMonthInYear: (Int) -> String,
        val onLastDayOfEveryMonth: () -> String,
    ) {
        fun monthName(month: Int): String = months[month - 1]
    }

    private companion object {
        val timeRegex = Regex("""^(\*|\d{4})-(\*|\d{2})-(\*|\d{2})(T\d{2}:\d{2}(:\d{2})?(Z|[+-]\d{2}:\d{2}))?$""")
        val zoneRegex = Regex("""^(Z|[+-]\d{2}:\d{2})$""")
        val weekdayRegex = Regex("""^(mon|tue|wed|thu|fri|sat|sun)$""")
        val locales =
            mapOf(
                "en" to
                    ClearTermsLocale(
                        language = "en",
                        months =
                            listOf(
                                "January",
                                "February",
                                "March",
                                "April",
                                "May",
                                "June",
                                "July",
                                "August",
                                "September",
                                "October",
                                "November",
                                "December",
                            ),
                        weekdays =
                            mapOf(
                                "mon" to "Monday",
                                "tue" to "Tuesday",
                                "wed" to "Wednesday",
                                "thu" to "Thursday",
                                "fri" to "Friday",
                                "sat" to "Saturday",
                                "sun" to "Sunday",
                            ),
                        decimalSeparator = '.',
                        groupingSeparator = ',',
                        atWord = "at",
                        onFullDate = { month, day, year -> "on $month $day, $year" },
                        inMonthYear = { month, year -> "in $month $year" },
                        onDayOfEveryMonthInYear = { day, year -> "on day $day of every month in $year" },
                        inYear = { year -> "in $year" },
                        onMonthDayEveryYear = { month, day -> "on $month $day every year" },
                        everyDayInMonth = { month -> "every day in $month" },
                        onDayOfEveryMonth = { day -> "on day $day of every month" },
                        everyDay = { "every day" },
                        onLastDayOfMonth = { month, year -> "on the last day of $month $year" },
                        onLastDayOfMonthEveryYear = { month -> "on the last day of $month every year" },
                        onLastDayOfEveryMonthInYear = { year -> "on the last day of every month in $year" },
                        onLastDayOfEveryMonth = { "on the last day of every month" },
                    ),
                "de" to
                    ClearTermsLocale(
                        language = "de",
                        months =
                            listOf(
                                "Januar",
                                "Februar",
                                "Maerz",
                                "April",
                                "Mai",
                                "Juni",
                                "Juli",
                                "August",
                                "September",
                                "Oktober",
                                "November",
                                "Dezember",
                            ),
                        weekdays =
                            mapOf(
                                "mon" to "Montag",
                                "tue" to "Dienstag",
                                "wed" to "Mittwoch",
                                "thu" to "Donnerstag",
                                "fri" to "Freitag",
                                "sat" to "Samstag",
                                "sun" to "Sonntag",
                            ),
                        decimalSeparator = ',',
                        groupingSeparator = '.',
                        atWord = "um",
                        onFullDate = { month, day, year -> "am $day. $month $year" },
                        inMonthYear = { month, year -> "im $month $year" },
                        onDayOfEveryMonthInYear = { day, year -> "am $day. jedes Monats im Jahr $year" },
                        inYear = { year -> "im Jahr $year" },
                        onMonthDayEveryYear = { month, day -> "am $day. $month jedes Jahr" },
                        everyDayInMonth = { month -> "jeden Tag im $month" },
                        onDayOfEveryMonth = { day -> "am $day. jedes Monats" },
                        everyDay = { "jeden Tag" },
                        onLastDayOfMonth = { month, year -> "am letzten Tag des $month $year" },
                        onLastDayOfMonthEveryYear = { month -> "am letzten Tag des $month jedes Jahr" },
                        onLastDayOfEveryMonthInYear = { year -> "am letzten Tag jedes Monats im Jahr $year" },
                        onLastDayOfEveryMonth = { "am letzten Tag jedes Monats" },
                    ),
            )
    }
}
