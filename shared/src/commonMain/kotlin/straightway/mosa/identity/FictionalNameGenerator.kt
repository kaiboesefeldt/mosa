package straightway.mosa.identity

import straightway.mosa.util.BaseGenerator
import straightway.mosa.util.CombiningGenerator
import straightway.mosa.util.anyOf
import straightway.mosa.util.concat
import straightway.mosa.util.times
import straightway.mosa.util.trans
import straightway.mosa.util.unaryPlus

class FictionalNameGenerator : BaseGenerator<FictionalName>() {
    override val size: Long
        get() = fullNameGenerator.size

    override operator fun get(index: Long): FictionalName = fullNameGenerator[index]

    // Für chinesische Aussprache günstige Anlaute (kein 'v', 'r' bleibt nur als Silbenanfang optional im InitialSet? -> wir lassen 'r' hier weg)
    private val initialSet = anyOf("ch", "b", "d", "f", "g", "h", "j", "k", "p", "t", "sh", "z", "zh")
    private val onset = anyOf("b", "d", "f", "l", "m", "n", "w", "s") // Anfangskonsonanten für Innen-/Schlusssilben

    // Vokale/Diphthonge – nur in Mandarin übliche/nahe Muster
    private val vowels = anyOf("a", "e", "i", "o", "u")
    private val diphthongs = anyOf("ai", "ei", "ao", "ou") // kein "oi"/"au"

    private val consonantVowel = +concat(onset, vowels)
    private val consonantDiphthong = +concat(onset, diphthongs)

    // Coda nur mit n/ng/y (vermeidet -l/-r/-s/-g/-m/-x)
    private val codaConsonants = anyOf("n", "ng", "y")

    private val surnameInitialSyllable = +concat(initialSet, vowels)
    private val surnameMiddleSyllable = anyOf(consonantVowel, consonantDiphthong)
    private val givenMiddleSyllable = +((0..1) * anyOf(consonantVowel, consonantDiphthong))

    // Nachnamen-Ende: nur Silben, die auf Vokal/-n/-ng/-y enden
    private val surnameFinalSyllable =
        anyOf(
            +concat(consonantVowel, codaConsonants), // z.B. "len", "sang", "wiy"
            +concat(onset, anyOf("y")),              // z.B. "ly", "ny"
            codaConsonants                           // z.B. "n", "ng", "y"
        )
    private val givenFinalSyllable = consonantVowel // Vokal-Ende ist für Rufnamen gut

    // Addition so wählen, dass sie selbst leicht ist (keine problematischen Endungen/Buchstaben)
    private val additionPrefix = anyOf("ya", "za", "lu", "ze", "sha", "zha", "cha")
    private val additionSuffix = anyOf("y", "n", "ng") // vermeidet q/x/th/c; Endet weich

    private val surnameGenerator =
        (
                +concat(
                    surnameInitialSyllable,
                    +((0..1) * surnameMiddleSyllable),
                    surnameFinalSyllable
                )
                ).trans {
                FictionalName(sureName = it.firstLetterUpperCase())
            }

    private fun givenNameGenerator() =
        (
                +concat(
                    vowels,
                    +((0..1) * givenMiddleSyllable),
                    givenFinalSyllable
                )
                ).trans {
                FictionalName(givenNames = listOf(it.firstLetterUpperCase()))
            }

    private val additionGenerator =
        (+concat(additionPrefix, additionSuffix)).trans {
            FictionalName(addendum = it) // bleibt klein geschrieben, Apostroph wird extern formatiert
        }

    private val fullNameGenerator =
        CombiningGenerator(
            listOf(
                givenNameGenerator(),
                givenNameGenerator(),
                additionGenerator,
                surnameGenerator
            )
        ).trans { generatedParts ->
            generatedParts.fold(FictionalName()) { accumulated, part ->
                accumulated.copy(
                    givenNames = accumulated.givenNames + part.givenNames,
                    sureName = accumulated.sureName + part.sureName,
                    addendum =
                        when {
                            accumulated.addendum == null -> part.addendum
                            part.addendum == null -> accumulated.addendum
                            else -> part.addendum + accumulated.addendum
                        }
                )
            }
        }

    private fun String.firstLetterUpperCase(): String =
        this[0].uppercase() + substring(1).lowercase()
}
