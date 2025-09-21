/**
 * Deterministischer Namensgenerator für Kotlin/JS.
 *
 * Aus der Kombination (seed, language, gender) wird stets derselbe Name erzeugt.
 * - 1..maxGivenNames Vornamen
 * - 1 oder 2 Nachnamen
 * - Nachnamen können zusammengesetzt sein (z.B. "Maurermann", "Maurermeier", "Mauererberg", "Stonebrook").
 *
 * Nutzung:
 *   val gen = DeterministicNameGenerator()
 *   val name = gen.generate(seed = 12345, language = Language.DE, gender = Gender.MALE, maxGivenNames = 3)
 */
class DeterministicNameGenerator(
    private val defaultMaxGivenNames: Int = 3
) {
    enum class Language(val num: Int) { DE(0), EN(1) }
    enum class Gender(val num: Int) { MALE(0), FEMALE(1) }

    /** Öffentliche API */
    fun generate(
        seed: Int,
        language: Language,
        gender: Gender,
        maxGivenNames: Int = defaultMaxGivenNames
    ): String {
        val rng = XorShift32(seed, language, gender)

        val givenCount = 1 + rng.nextInt(maxGivenNames.coerceAtLeast(1))
        val givenNames = when (language) {
            Language.DE -> when (gender) {
                Gender.MALE -> pickDistinct(rng, deMaleGiven, givenCount)
                Gender.FEMALE -> pickDistinct(rng, deFemaleGiven, givenCount)
            }
            Language.EN -> when (gender) {
                Gender.MALE -> pickDistinct(rng, enMaleGiven, givenCount)
                Gender.FEMALE -> pickDistinct(rng, enFemaleGiven, givenCount)
            }
        }

        val surnameCount = if (rng.nextInt(100) < 50) 1 else 2
        val surnames = when (language) {
            Language.DE -> List(surnameCount) { generateGermanSurname(rng) }
            Language.EN -> List(surnameCount) { generateEnglishSurname(rng) }
        }.let {
            if (surnameCount == 2 && it.first() == it.last()) listOf(it.first()) else it
        }

        val joinedSurname = if (surnames.size == 2) {
            "${surnames[0]}-${surnames[1]}"
        } else {
            surnames.joinToString(" ")
        }

        return (givenNames + joinedSurname).joinToString(" ")
    }

    // ---------------- Internals ----------------

    /** Einfache deterministische PRNG (xorshift32) – JS-kompatibel, plattformstabil. */
    private class XorShift32(seed: Int, language: Language, gender: Gender) {
        private var state: UInt

        init {
            // Kombiniert Seed mit Language/Gender, damit Streams pro (seed, lang, gender) eindeutig sind.
            var s = seed.toUInt()
            s = s xor (0x9E3779B9u * (language.ordinal + 1).toUInt())
            s = s xor (0x85EBCA6Bu * (gender.ordinal + 1).toUInt())
            if (s == 0u) s = 0x12345678u
            state = s
        }

        fun nextUInt(): UInt {
            var x = state
            x = x xor (x shl 13)
            x = x xor (x shr 17)
            x = x xor (x shl 5)
            state = x
            return x
        }

        fun nextInt(bound: Int): Int {
            require(bound > 0) { "bound must be > 0" }
            val x = (nextUInt() and 0x7FFFFFFFu.toUInt()).toInt()
            return x % bound
        }
    }

    private fun pickDistinct(rng: XorShift32, pool: List<String>, count: Int): List<String> {
        if (count >= pool.size) return pool.shuffled(rng).take(count) // Fallback
        val result = ArrayList<String>(count)
        while (result.size < count) {
            val cand = pool[rng.nextInt(pool.size)]
            if (cand !in result) result += cand
        }
        return result
    }

    /** Shuffle-Extension mit unserem RNG (für JS ohne kotlin.random.Random-Implementierung). */
    private fun <T> List<T>.shuffled(rng: XorShift32): List<T> {
        val a = this.toMutableList()
        for (i in a.indices.reversed()) {
            val j = if (i == 0) 0 else rng.nextInt(i + 1)
            val tmp = a[i]; a[i] = a[j]; a[j] = tmp
        }
        return a
    }

    // ---------- Deutsche Nachnamen (zusammensetzbar) ----------
    private val deSurnameStems = listOf(
        "Maurer", "Mauer", "Bauer", "Schmied", "Stein", "Wald", "Berg", "Hoff", "Meyer",
        "Meier", "Müller", "Weber", "Fischer", "Schneider", "Kauf", "Brand", "Holz", "Korn",
        "Schuh", "Schreiner", "Wagner", "Lehner", "Gruber", "Koch", "Richter", "Wolf",
        "Neumann", "Schwarz", "Zimmer"
    )

    private val deSurnameSuffixes = listOf(
        "mann", "meier", "lein", "berg", "stein", "wald", "dorf", "bach", "feld",
        "brück", "hoff", "huber", "inger", "haus", "thal", "furth", "hardt", "stett",
        "acker", "weiler", "hofer", "bauer"
    )

    private val deBaseSurnames = listOf(
        "Schmidt", "Müller", "Schneider", "Fischer", "Weber", "Meyer", "Wagner", "Becker",
        "Hoffmann", "Schäfer", "Koch", "Richter", "Klein", "Neumann", "Zimmermann",
        "Schwarz", "Bauer", "Krüger", "Hartmann", "Werner", "Krause", "Wolf", "Schröder",
        "Maier", "Schmid", "Keller", "Kaiser", "Fuchs", "Vogel", "Sommer"
    )

    private fun generateGermanSurname(rng: XorShift32): String {
        // Hälfte der Fälle: fertiger Basisnachname; sonst komponieren.
        return if (rng.nextInt(100) < 50) {
            deBaseSurnames[rng.nextInt(deBaseSurnames.size)]
        } else {
            val stem = deSurnameStems[rng.nextInt(deSurnameStems.size)]
            val suffix = deSurnameSuffixes[rng.nextInt(deSurnameSuffixes.size)]
            val joiner = chooseGermanJoiner(rng, stem, suffix)
            (stem + joiner + suffix).replace("..", ".") // no-op, placeholder to avoid lint
        }
    }

    /** Fügt zwischen Stamm und Suffix gelegentlich "e" oder "er" ein (z.B. Mauer + er + berg). */
    private fun chooseGermanJoiner(rng: XorShift32, stem: String, suffix: String): String {
        if (stem.endsWith("er")) return ""            // Maurer + mann => Maurermann
        if (suffix.startsWith("e")) return ""         // seltene Fälle
        return when {
            rng.nextInt(100) < 50 -> "er"            // Mauer + er + berg => Mauererberg
            rng.nextInt(100) < 40 -> "e"             // Kauf + e + mann => Kaufemann (kommt vor)
            else -> ""                                // Standard: direkte Zusammensetzung
        }
    }

    // ---------- Englische Nachnamen (zusammensetzbar) ----------
    private val enSurnameStems = listOf(
        "Stone", "Wood", "Brook", "Field", "Hill", "Ford", "Lake", "Bridge",
        "Green", "Black", "White", "Oak", "Ash", "Birch", "Rock", "Clay",
        "North", "South", "East", "West"
    )

    private val enSurnameSuffixes = listOf(
        "son", "ton", "wood", "field", "ford", "brook", "stone", "hill",
        "dale", "ridge", "burn", "man", "well", "worth", "gate", "view"
    )

    private val enBaseSurnames = listOf(
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Miller", "Davis", "Garcia",
        "Rodriguez", "Wilson", "Martinez", "Anderson", "Taylor", "Thomas", "Moore",
        "Jackson", "Martin", "Lee", "Thompson", "White", "Harris", "Clark", "Lewis",
        "Walker", "Young", "Hall", "Allen", "King", "Wright", "Scott", "Baker", "Fisher",
        "Carter", "Cooper", "Cook", "Parker"
    )

    private fun generateEnglishSurname(rng: XorShift32): String {
        return if (rng.nextInt(100) < 50) {
            enBaseSurnames[rng.nextInt(enBaseSurnames.size)]
        } else {
            val stem = enSurnameStems[rng.nextInt(enSurnameStems.size)]
            val suffix = enSurnameSuffixes[rng.nextInt(enSurnameSuffixes.size)]
            val joiner = chooseEnglishJoiner(rng, stem, suffix)
            stem + joiner + suffix // z.B. Stone + brook => Stonebrook
        }
    }

    private fun chooseEnglishJoiner(rng: XorShift32, stem: String, suffix: String): String {
        // Kleine Phonetik: "e" am Ende von stem + vokalisch anmutendes Suffix -> kein Joiner
        if (stem.endsWith("e")) return ""
        return if (rng.nextInt(100) < 20) "" else "" // bewusst simpel; Erweiterung möglich
    }

    // ---------- Vornamen-Pools ----------
    private val deMaleGiven = listOf(
        "Max", "Paul", "Ben", "Elias", "Leon", "Finn", "Noah", "Luis", "Jonas", "Luca",
        "Henry", "Emil", "Lukas", "Felix", "Theo", "Jakob", "Mats", "Anton", "Oskar", "Nico",
        "Mika", "Johann", "Moritz", "Levi", "David", "Fabian", "Jannik", "Julian", "Tobias", "Matteo"
    )

    private val deFemaleGiven = listOf(
        "Mia", "Emma", "Emilia", "Hannah", "Sofia", "Lea", "Lina", "Ella", "Marie", "Anna",
        "Lena", "Clara", "Luisa", "Frieda", "Lilly", "Amelie", "Charlotte", "Ida", "Maja", "Nora",
        "Greta", "Mathilda", "Sara", "Marlene", "Johanna", "Helena", "Laura", "Paula", "Sophie", "Isabell"
    )

    private val enMaleGiven = listOf(
        "Liam", "Noah", "Oliver", "Elijah", "James", "William", "Benjamin", "Lucas", "Henry", "Alexander",
        "Mason", "Michael", "Ethan", "Daniel", "Jacob", "Logan", "Jackson", "Levi", "Sebastian", "Jack",
        "Owen", "Theodore", "Aiden", "Samuel", "Joseph", "John", "David", "Wyatt", "Matthew", "Luke"
    )

    private val enFemaleGiven = listOf(
        "Olivia", "Emma", "Ava", "Charlotte", "Sophia", "Amelia", "Isabella", "Mia", "Evelyn", "Harper",
        "Luna", "Camila", "Gianna", "Elizabeth", "Eleanor", "Ella", "Abigail", "Sofia", "Avery", "Scarlett",
        "Emily", "Aria", "Penelope", "Chloe", "Layla", "Mila", "Nora", "Hazel", "Lily", "Zoey"
    )
}
