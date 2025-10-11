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

    // Robuste, einfache Anlaute (bewusst ohne zh/sh/ch/sch, r, v, p, j, q, x, c)
    private val initialSet = anyOf("b", "d", "f", "g", "h", "k", "l", "m", "n", "s", "t", "w", "y", "z")
    private val onset      = anyOf("b", "d", "f", "g", "h", "k", "l", "m", "n", "s", "t", "w", "y", "z")

    // Nur einfache Vokale – keine Diphthonge -> verhindert VV-Sequenzen
    private val vowels = anyOf("a", "e", "i", "o", "u")

    private val consonantVowel = +concat(onset, vowels)

    // Nachname: muss mit Konsonant beginnen (initialSet) und auf Konsonant enden (-n)
    private val surnameInitialSyllable = +concat(initialSet, vowels)          // CV
    private val surnameMiddleSyllable  = consonantVowel                       // CV
    private val surnameFinalSyllable   = +concat(consonantVowel, anyOf("n"))  // CV + n  => ...-n

    // Vorname: (V)(CV){0..1} + CV  -> endet auf Vokal, aber ohne VV da Mittelsilbe CV ist
    private val givenMiddleSyllable = +((0..1) * consonantVowel)
    private val givenFinalSyllable  = consonantVowel

    // Addition: nur einfache Präfixe; Suffix ausschließlich -n (verhindert Doppelvokal)
    private val additionPrefix = anyOf("sa", "za", "la", "na", "ya", "wa")
    private val additionSuffix = anyOf("n")

    private val surnameGenerator =
        (
                +concat(
                    surnameInitialSyllable,
                    +((0..1) * surnameMiddleSyllable),  // 1–2 CV-Silben vor dem Abschluss
                    surnameFinalSyllable                 // ...-n
                )
                ).trans {
                FictionalName(sureName = it.firstLetterUpperCase())
            }

    private fun givenNameGenerator() =
        (
                +concat(
                    vowels,                               // V
                    +((0..1) * givenMiddleSyllable),      // (CV){0..1}
                    givenFinalSyllable                    // CV
                )
                ).trans {
                FictionalName(givenNames = listOf(it.firstLetterUpperCase()))
            }

    private val additionGenerator =
        (+concat(additionPrefix, additionSuffix)).trans {
            FictionalName(addendum = it) // bleibt kleingeschrieben; Apostroph wird extern formatiert
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
                    sureName   = accumulated.sureName + part.sureName,
                    addendum   =
                        when {
                            accumulated.addendum == null -> part.addendum
                            part.addendum == null        -> accumulated.addendum
                            else                         -> part.addendum + accumulated.addendum
                        }
                )
            }
        }

    private fun String.firstLetterUpperCase(): String =
        this[0].uppercase() + substring(1).lowercase()
}
