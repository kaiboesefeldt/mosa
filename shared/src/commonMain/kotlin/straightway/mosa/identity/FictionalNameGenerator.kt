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

    private val initialSet = anyOf("ch", "b", "d", "f", "g", "h", "j", "k", "p", "r", "t", "v", "sh", "z", "zh")
    private val onset = anyOf("b", "d", "f", "l", "m", "n", "w", "s")
    private val vowels = anyOf("a", "e", "i", "o", "u")
    private val diphthongs = anyOf("au", "ai", "ei", "oi")
    private val consonantVowel = +concat(onset, vowels)
    private val consonantDiphthong = +concat(onset, diphthongs)

    private val codaConsonants = anyOf("g", "n", "m", "r", "l", "s", "r", "ng")

    private val surnameInitialSyllable = +concat(initialSet, vowels)
    private val surnameMiddleSyllable = anyOf(consonantVowel, consonantDiphthong)
    private val givenMiddleSyllable = +((0..1) * anyOf(consonantVowel, consonantDiphthong))

    private val surnameFinalSyllable =
        anyOf(
            +concat(consonantVowel, codaConsonants),
            +concat(onset, anyOf("y")),
            codaConsonants,
        )
    private val givenFinalSyllable = consonantVowel

    private val additionPrefix = anyOf("y", "za", "qi", "vo", "ka", "lu", "ze", "tra")
    private val additionSuffix = anyOf("q", "x", "th", "c")

    private val surnameGenerator =
        (
            +concat(
                surnameInitialSyllable,
                +((0..1) * surnameMiddleSyllable),
                surnameFinalSyllable,
            )
        ).trans {
            FictionalName(sureName = it.firstLetterUpperCase())
        }

    private fun givenNameGenerator() =
        (
            +concat(
                vowels,
                +((0..1) * givenMiddleSyllable),
                givenFinalSyllable,
            )
        ).trans {
            FictionalName(givenNames = listOf(it.firstLetterUpperCase()))
        }

    private val additionGenerator =
        (+concat(additionPrefix, additionSuffix)).trans {
            FictionalName(addendum = it)
        }

    private val fullNameGenerator =
        CombiningGenerator(
            listOf(
                givenNameGenerator(),
                givenNameGenerator(),
                additionGenerator,
                surnameGenerator,
            ),
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
                        },
                )
            }
        }

    private fun String.firstLetterUpperCase(): String = this[0].uppercase() + substring(1).lowercase()
}
