package straightway.mosa.identity

import straightway.mosa.util.BitStream
import straightway.mosa.util.Generator
import straightway.mosa.util.anyOf
import straightway.mosa.util.bitsRequiredForMax
import straightway.mosa.util.combine
import straightway.mosa.util.concat
import straightway.mosa.util.times
import straightway.mosa.util.trans
import straightway.mosa.util.unaryPlus
import kotlin.math.max

class FictionalNameGenerator {
    val size: Double
        get() = fullNameParts.fold(1.0) { acc, i -> acc * i.size.toDouble() }

    val bits: Int
        get() = fullNameParts.fold(0) { acc, i -> acc + bitsRequiredForMax(i.size) }

    fun generate(bits: BitStream): FictionalName =
        fullNameParts.fold(FictionalName()) { acc, i -> acc + i.generateFrom(bits) }

    private fun Generator<FictionalName>.generateFrom(bits: BitStream): FictionalName {
        val requiredBits = bitsRequiredForMax(size)
        val nextIndex = bits.nextBitsLong(requiredBits)
        return this[nextIndex % size]
    }

    private val initialSet =
        anyOf("sh", "ch", "b", "d", "f", "g", "h", "k", "l", "m", "n", "r", "s", "t", "w", "z")
    private val onset =
        anyOf("b", "d", "f", "g", "k", "l", "m", "n", "r", "s", "t", "w", "z")

    private val vowels = anyOf("a", "e", "i", "o", "u")
    private val givenNameEndVowels = anyOf("e", "i", "u", "y")
    private val safeDiph = anyOf("ai", "ei")

    private val consonantVowel = +concat(onset, vowels)
    private val consonantSafeDiph = +concat(onset, safeDiph)
    private val consonantFinalGivenNameVowel = +concat(onset, givenNameEndVowels)

    private val endCons = anyOf("n", "m", "l", "s")

    private val additionPrefix = +concat(onset, vowels)
    private val additionSuffix = endCons

    private fun surnameGenerator(syllableCount: Int) =
        (
            anyOf(
                +concat(
                    initialSet,
                    vowels,
                    anyOf(
                        +combine(consonantSafeDiph, +((0..(syllableCount - 2)) * consonantVowel)),
                        +((0..(syllableCount - 1)) * consonantVowel),
                    ),
                    endCons,
                ),
                +concat(
                    initialSet,
                    safeDiph,
                    +((0..(syllableCount - 1)) * consonantVowel),
                    endCons,
                ),
            )
        ).trans { FictionalName(sureName = it.firstLetterUpperCase()) }

    private fun givenNameGenerator(syllableCount: Int): Generator<FictionalName> =
        (
            listOf(consonantSafeDiph, consonantVowel) +
            (2..max(1, syllableCount - 1)).flatMap { numSyllables ->
                listOf(
                    +combine(+((numSyllables - 1) * consonantVowel), consonantSafeDiph),
                    +(numSyllables * consonantVowel),
                )
            }
        ).let { it: List<Generator<String>> ->
             val initSyllablesGen: Generator<String> = anyOf(*it.toTypedArray())
            (+concat(initSyllablesGen, consonantFinalGivenNameVowel)).trans {
                FictionalName(givenNames = listOf(it.firstLetterUpperCase()))
            }
        }

    private val additionGenerator =
        (+concat(additionPrefix, additionSuffix)).trans {
            FictionalName(addendum = it)
        }

    private val fullNameParts = listOf(
        givenNameGenerator(syllableCount = 2),
        givenNameGenerator(syllableCount = 3),
        additionGenerator,
        surnameGenerator(syllableCount = 3),
    )

    private fun String.firstLetterUpperCase(): String =
        this[0].uppercase() + substring(1).lowercase()
}
