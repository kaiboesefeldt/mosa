package straightway.mosa.client.identity

class InternationalSurnameGenerator {

    // ===== Public API =====
    fun generate(bytes: ByteArray): String {
        val bs = BitStream(bytes)
        val langIndex = bs.nextInt(profiles.size)
        val p = profiles[langIndex]
        val isMale = when (p.genderMode) {
            GenderMode.NONE -> true // ignored later
            GenderMode.BINARY -> bs.nextInt(2) == 1
        }

        // Build first surname (according to language rules)
        val name1 = buildSingleSurname(p, bs, isMale)

        // Optional double surname within the same language
        if (p.supportsDouble) {
            val takeDouble = bs.nextInt(2) == 1
            if (takeDouble) {
                var name2 = buildSingleSurname(p, bs, isMale)
                // avoid exact duplicates deterministically
                var guard = 0
                while (normalizeToken(name2) == normalizeToken(name1) && guard < 3) {
                    name2 = buildSingleSurname(p, bs, isMale)
                    guard++
                }
                if (normalizeToken(name2) != normalizeToken(name1)) {
                    return p.doubleJoinerJoin(name1, name2)
                }
            }
        }
        return name1
    }

    // ===== Core building =====
    private fun buildSingleSurname(p: Profile, bs: BitStream, isMale: Boolean): String {
        // Decide pattern (degenerates automatically if some lists are empty or compounds not supported)
        val patterns = effectivePatterns(p)
        val pattern = patterns[bs.nextInt(patterns.size)]

        val chosen = mutableListOf<String>()
        var prefix = ""
        var stem = ""
        var suffix = ""

        // Stem is always required
        if (p.stems.isNotEmpty()) {
            val stemIdx = pickDistinctIndex(bs, p.stems, chosen)
            stem = p.stems[stemIdx]
            chosen.add(stem)
        }

        // Optional prefix
        if ((pattern.hasPrefix) && p.prefixes.isNotEmpty()) {
            val prefixIdx = pickDistinctIndex(bs, p.prefixes, chosen)
            prefix = p.prefixes[prefixIdx]
            // avoid duplicates like "Bauerbauer" if a prefix equals stem textually
            if (normalizeToken(prefix) != normalizeToken(stem)) chosen.add(prefix)
        }

        // Optional suffix
        if ((pattern.hasSuffix) && p.suffixes.isNotEmpty()) {
            val suffixIdx = pickDistinctIndex(bs, p.suffixes, chosen)
            suffix = p.suffixes[suffixIdx]
            if (normalizeToken(suffix) != normalizeToken(stem)) chosen.add(suffix)
        }

        // Assemble per language join rules
        var name = assemble(prefix, stem, suffix, p)

        // Gender adjustments (only for languages that use it)
        if (p.genderMode == GenderMode.BINARY && p.genderPost != null) {
            name = p.genderPost.invoke(name, isMale)
        }

        // Lightweight normalization
        name = name.replace(Regex("\\s+"), " ").trim()
        name = name.replace(Regex("\\s*-\\s*"), "-") // tight hyphens

        // Final language-specific normalization hook (rarely needed)
        if (p.finalize != null) name = p.finalize.invoke(name)

        return name
    }

    // ===== Patterns =====
    private enum class Pattern(val hasPrefix: Boolean, val hasSuffix: Boolean) {
        PRIMITIVE(false, false),            // Stem
        PREFIX_STEM(true, false),           // Prefix + Stem
        STEM_SUFFIX(false, true),           // Stem + Suffix
        PREFIX_STEM_SUFFIX(true, true)      // Prefix + Stem + Suffix
    }

    private fun effectivePatterns(p: Profile): List<Pattern> {
        val list = mutableListOf<Pattern>()
        list.add(Pattern.PRIMITIVE)
        if (p.supportsCompound) {
            if (p.prefixes.isNotEmpty()) list.add(Pattern.PREFIX_STEM)
            if (p.suffixes.isNotEmpty()) list.add(Pattern.STEM_SUFFIX)
            if (p.prefixes.isNotEmpty() && p.suffixes.isNotEmpty()) list.add(Pattern.PREFIX_STEM_SUFFIX)
        }
        return list
    }

    private fun assemble(prefix: String, stem: String, suffix: String, p: Profile): String {
        val sb = StringBuilder()
        if (prefix.isNotEmpty()) {
            sb.append(prefix)
            sb.append(p.prefixJoiner)
        }
        sb.append(stem)
        if (suffix.isNotEmpty()) {
            sb.append(p.suffixJoiner)
            sb.append(suffix)
        }
        return sb.toString()
    }

    private fun pickDistinctIndex(bs: BitStream, arr: Array<String>, already: List<String>): Int {
        if (arr.isEmpty()) return 0
        val forbidden = already.map { normalizeToken(it) }.toMutableSet()
        // Try fairly to avoid duplicates; if impossible, fall back deterministically
        val limit = arr.size * 2 + 3
        var tries = 0
        while (tries < limit) {
            val idx = bs.nextInt(arr.size)
            val token = normalizeToken(arr[idx])
            if (!forbidden.contains(token)) return idx
            tries++
        }
        return bs.nextInt(arr.size)
    }

    private fun normalizeToken(s: String): String =
        s.lowercase().replace(Regex("[\\s\\-’'`]"), "")

    // ===== Bit source with unbiased selection =====
    private class BitStream(private val bytes: ByteArray) {
        private var byteIdx = 0
        private var bitIdx = 0 // 0..7, MSB-first

        fun nextBit(): Int {
            if (byteIdx >= bytes.size) return 0 // zero-padding when exhausted
            val b = bytes[byteIdx].toInt() and 0xFF
            val bit = (b shr (7 - bitIdx)) and 1
            bitIdx++
            if (bitIdx == 8) {
                bitIdx = 0
                byteIdx++
            }
            return bit
        }

        // Unbiased choice in [0, n)
        fun nextInt(n: Int): Int {
            require(n > 0)
            if (n == 1) return 0
            val k = ceilLog2(n)
            val max = 1 shl k
            val limit = max - (max % n) // threshold for rejection
            while (true) {
                var v = 0
                repeat(k) { v = (v shl 1) or nextBit() }
                if (v < limit) return v % n
                // else: reject and read another k bits (bits are still consumed, no gaps)
            }
        }

        private fun ceilLog2(n: Int): Int {
            var x = n - 1
            var r = 0
            while (x > 0) {
                x = x shr 1
                r++
            }
            return if (r == 0) 1 else r
        }
    }

    // ===== Language profiles =====
    private enum class GenderMode { NONE, BINARY }

    private data class Profile(
        val language: String,
        val prefixes: Array<String> = emptyArray(),
        val stems: Array<String>,
        val suffixes: Array<String> = emptyArray(),
        val supportsCompound: Boolean = false,
        val supportsDouble: Boolean = false,
        val doubleJoiner: String = "-",
        val genderMode: GenderMode = GenderMode.NONE,
        val prefixJoiner: String = "",
        val suffixJoiner: String = "",
        val doubleJoinerJoin: (String, String) -> String = { a, b -> "$a$doubleJoiner$b" },
        val genderPost: ((String, Boolean) -> String)? = null,
        val finalize: ((String) -> String)? = null
    )

    private val profiles: Array<Profile> = arrayOf(
        // 1. Deutsch
        Profile(
            language = "Deutsch",
            prefixes = arrayOf("Groß", "Klein", "Ober", "Unter"),
            stems = arrayOf("Mayer", "Bauer", "Schmidt", "Wagner", "Keller", "Weber", "Fischer", "Becker", "Schneider", "Hoffmann"),
            suffixes = arrayOf("mann", "hofer", "berg", "stein", "bauer", "wald", "haus", "feld"),
            supportsCompound = true,
            supportsDouble = true,
            doubleJoiner = "-",
            prefixJoiner = "",
            suffixJoiner = ""
        ),

        // 2. Englisch
        Profile(
            language = "English",
            prefixes = arrayOf("Mac", "Mc", "O'"),
            stems = arrayOf("Smith", "Johnson", "Brown", "Taylor", "Miller", "Davis", "Wilson", "Clark", "Wright", "Walker"),
            suffixes = arrayOf("son", "ton", "man", "wood", "field"),
            supportsCompound = true,
            supportsDouble = true,
            doubleJoiner = "-",
            prefixJoiner = "",
            suffixJoiner = ""
        ),

        // 3. Französisch
        Profile(
            language = "Français",
            prefixes = arrayOf("de", "du", "le", "la"),
            stems = arrayOf("Lefèvre", "Dubois", "Moreau", "Laurent", "Simon", "Mercier", "Roux", "Fournier", "Legrand", "Girard"),
            supportsCompound = true,
            supportsDouble = true,
            doubleJoiner = "-",
            prefixJoiner = " ",
            suffixJoiner = "",
            finalize = { s ->
                // keep particles lowercase; title-case other parts separated by space or hyphen
                s.split(' ').joinToString(" ") { part ->
                    val lowParticles = setOf("de", "du", "le", "la")
                    if (lowParticles.contains(part.lowercase())) part.lowercase() else titleCaseTokens(part)
                }
            }
        ),

        // 4. Polski (gendered -ski/-ska etc.)
        Profile(
            language = "Polski",
            stems = arrayOf("Nowak", "Kowalski", "Wiśniewski", "Wójcik", "Kamiński", "Lewandowski", "Zieliński", "Szymański", "Dąbrowski", "Mazur"),
            supportsCompound = false,
            supportsDouble = true,
            doubleJoiner = "-",
            genderMode = GenderMode.BINARY,
            genderPost = { name, male ->
                if (male) name else when {
                    name.endsWith("ski") -> name.dropLast(3) + "ska"
                    name.endsWith("cki") -> name.dropLast(3) + "cka"
                    name.endsWith("dzki") -> name.dropLast(4) + "dzka"
                    else -> name
                }
            }
        ),

        // 5. Svenska (gendered patronymics possible)
        Profile(
            language = "Svenska",
            prefixes = emptyArray(),
            stems = arrayOf("Anders", "Johans", "Karls", "Lars", "Nils", "Olofs", "Berg", "Lind", "Sjö"),
            suffixes = arrayOf("sson", "sdotter", "berg", "ström", "lund"),
            supportsCompound = true,
            supportsDouble = true,
            doubleJoiner = "-",
            genderMode = GenderMode.BINARY,
            suffixJoiner = "",
            genderPost = { name, male ->
                if (male) {
                    if (name.endsWith("sdotter")) name.dropLast(8) + "sson" else name
                } else {
                    if (name.endsWith("sson")) name.dropLast(4) + "sdotter" else name
                }
            }
        ),

        // 6. Suomi (Finnish)
        Profile(
            language = "Suomi",
            stems = arrayOf("Korhonen", "Virtanen", "Mäkinen", "Nieminen", "Mäkelä", "Hämäläinen", "Laine", "Heikkinen", "Koskinen", "Järvinen"),
            supportsCompound = false,
            supportsDouble = true,
            doubleJoiner = "-"
        ),

        // 7. Українська (Ukrainian)
        Profile(
            language = "Ukrainian",
            stems = arrayOf("Shevchenko", "Melnyk", "Kovalenko", "Boyko", "Tkachenko", "Bondarenko", "Kravchenko", "Kovalchuk", "Moroz", "Lysenko"),
            supportsCompound = false,
            supportsDouble = true,
            doubleJoiner = "-"
        ),

        // 8. Русский (Russian, gendered endings)
        Profile(
            language = "Russkij",
            stems = arrayOf("Ivanov", "Petrov", "Sokolov", "Smirnov", "Pawlow", "Kuznetsov", "Pushkin", "Tchaikovsky", "Egorov", "Loginov"),
            supportsCompound = false,
            supportsDouble = true,
            doubleJoiner = "-",
            genderMode = GenderMode.BINARY,
            genderPost = { name, male ->
                if (male) name else when {
                    name.endsWith("ov") -> name + "a"
                    name.endsWith("ev") -> name + "a"
                    name.endsWith("in") -> name + "a"
                    name.endsWith("sky") -> name.dropLast(3) + "skaya"
                    name.endsWith("ski") -> name.dropLast(3) + "skaya"
                    else -> name
                }
            }
        ),

        // 9. 中文 (Chinese; already in Latin, e.g., Pinyin without tones)
        Profile(
            language = "Chinese",
            stems = arrayOf("Wang", "Li", "Zhang", "Liu", "Chen", "Yang", "Zhao", "Huang", "Zhou", "Wu", "Xu"),
            supportsCompound = false,
            supportsDouble = false
        ),

        // 10. العربية (Arabic; using Latinized forms; common 'Al-' prefix)
        Profile(
            language = "Arabic",
            prefixes = arrayOf("Al-"),
            stems = arrayOf("Najjar", "Haddad", "Amiri", "Khatib", "Farouq", "Fares", "Hassan", "Jaber", "Sabry", "Rahman"),
            supportsCompound = true,
            supportsDouble = true,
            doubleJoiner = " ",
            prefixJoiner = "", // 'Al-' sticks to the stem
            suffixJoiner = ""
        ),

        // 11. Kiswahili
        Profile(
            language = "Kiswahili",
            stems = arrayOf("Juma", "Nyerere", "Mwangi", "Mushi", "Omari", "Salim", "Hassan", "Kilonzo", "Mbele", "Mabula"),
            supportsCompound = false,
            supportsDouble = true,
            doubleJoiner = "-"
        ),

        // 12. Afrikaans
        Profile(
            language = "Afrikaans",
            prefixes = arrayOf("van", "van der", "de", "du"),
            stems = arrayOf("Merwe", "Wyk", "Klerk", "Plessis", "Villiers", "Rensburg", "Botha", "Steyn", "Erasmus", "Oosthuizen"),
            supportsCompound = true,
            supportsDouble = true,
            doubleJoiner = "-",
            prefixJoiner = " ",
            suffixJoiner = "",
            finalize = { s ->
                // keep particles lowercase, title-case stems
                s.split(' ').joinToString(" ") { part ->
                    val particles = setOf("van", "van der", "de", "du")
                    if (particles.contains(part.lowercase())) part.lowercase() else titleCaseTokens(part)
                }
            }
        ),

        // 13. हिंदी (Hindi; Latinized)
        Profile(
            language = "Hindi",
            stems = arrayOf("Sharma", "Verma", "Gupta", "Agarwal", "Singh", "Chaudhary", "Patel", "Joshi", "Mehta", "Kapoor"),
            supportsCompound = false,
            supportsDouble = true,
            doubleJoiner = "-"
        ),

        // 14. اردو (Urdu; Latinized)
        Profile(
            language = "Urdu",
            stems = arrayOf("Khan", "Qureshi", "Ansari", "Malik", "Syed", "Sheikh", "Ahmed", "Raza", "Hussain", "Rizvi"),
            supportsCompound = false,
            supportsDouble = true,
            doubleJoiner = "-"
        ),

        // 15. Bahasa Indonesia
        Profile(
            language = "Indonesia",
            stems = arrayOf("Putra", "Putri", "Saputra", "Santoso", "Nugroho", "Hidayat", "Pratama", "Wijaya", "Siregar", "Ramadhan"),
            supportsCompound = false,
            supportsDouble = true,
            doubleJoiner = "-"
        ),

        // 16. Italiano
        Profile(
            language = "Italiano",
            prefixes = arrayOf("di", "de", "della", "del"),
            stems = arrayOf("Rossi", "Russo", "Ferrari", "Esposito", "Bianchi", "Romano", "Gallo", "Costa", "Giordano", "Mancini"),
            supportsCompound = true,
            supportsDouble = true,
            doubleJoiner = "-",
            prefixJoiner = " ",
            suffixJoiner = "",
            finalize = { s ->
                s.split(' ').joinToString(" ") { part ->
                    val particles = setOf("di", "de", "della", "del")
                    if (particles.contains(part.lowercase())) part.lowercase() else titleCaseTokens(part)
                }
            }
        ),

        // 17. Türkçe
        Profile(
            language = "Türkçe",
            stems = arrayOf("Yılmaz", "Kaya", "Demir", "Şahin", "Çelik", "Yıldız", "Aydın", "Öztürk", "Kılıç", "Arslan"),
            supportsCompound = false,
            supportsDouble = true,
            doubleJoiner = "-"
        ),

        // 18. Português
        Profile(
            language = "Português",
            prefixes = arrayOf("da", "de", "do", "dos", "das"),
            stems = arrayOf("Silva", "Santos", "Ferreira", "Pereira", "Oliveira", "Sousa", "Carvalho", "Gomes", "Martins", "Rocha"),
            supportsCompound = true,
            supportsDouble = true,
            doubleJoiner = " ", // space between the two surnames
            doubleJoinerJoin = { a, b -> "$a $b" },
            prefixJoiner = " ",
            suffixJoiner = "",
            finalize = { s ->
                s.split(' ').joinToString(" ") { part ->
                    val particles = setOf("da", "de", "do", "dos", "das")
                    if (particles.contains(part.lowercase())) part.lowercase() else titleCaseTokens(part)
                }
            }
        ),

        // 19. Español
        Profile(
            language = "Español",
            prefixes = arrayOf("de", "del", "de la"),
            stems = arrayOf("García", "Martínez", "López", "Sánchez", "González", "Pérez", "Rodríguez", "Fernández", "Gómez", "Díaz"),
            supportsCompound = true,
            supportsDouble = true,
            doubleJoiner = " ",
            doubleJoinerJoin = { a, b -> "$a $b" },
            prefixJoiner = " ",
            suffixJoiner = "",
            finalize = { s ->
                s.split(' ').joinToString(" ") { part ->
                    val particles = setOf("de", "del", "de la")
                    if (particles.contains(part.lowercase())) part.lowercase() else titleCaseTokens(part)
                }
            }
        ),

        // 20. Nederlands
        Profile(
            language = "Nederlands",
            prefixes = arrayOf("de", "van", "van der", "van den"),
            stems = arrayOf("Vries", "Berg", "Bakker", "Jansen", "Visser", "Bos", "Smit", "Mulder", "Dekker", "Kok"),
            supportsCompound = true,
            supportsDouble = true,
            doubleJoiner = "-",
            prefixJoiner = " ",
            suffixJoiner = "",
            finalize = { s ->
                s.split(' ').joinToString(" ") { part ->
                    val particles = setOf("de", "van", "van der", "van den")
                    if (particles.contains(part.lowercase())) part.lowercase() else titleCaseTokens(part)
                }
            }
        )
    )

    // ===== Small helpers =====
    companion object {
        private fun titleCaseTokens(token: String): String {
            // Title-case for hyphenated/apostrophe-separated subtokens
            val hyParts = token.split('-')
            return hyParts.joinToString("-") { hp ->
                val ap = hp.split(Regex("(?<=')"))
                ap.joinToString("") { sub ->
                    if (sub.isEmpty()) sub else sub.substring(0, 1).uppercase() + sub.substring(1).lowercase()
                }
            }
        }
    }
}
