package straightway.mosa.identity

import straightway.mosa.util.BitStream
import straightway.mosa.util.Generator
import straightway.mosa.util.anyOf
import straightway.mosa.util.bitsRequiredForMax
import straightway.mosa.util.concat
import straightway.mosa.util.times
import straightway.mosa.util.trans
import straightway.mosa.util.unaryPlus

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
        anyOf("sh", "ch", "b", "d", "f", "g", "h", "k", "l", "m", "n", "r", "s", "t", "w", "y", "z")
    private val onset =
        anyOf("sh", "ch", "b", "d", "f", "g", "h", "k", "l", "m", "n", "r", "s", "t", "w", "y", "z")

    private val vowels = anyOf("a", "e", "i", "o", "u")
    private val safeDiph = anyOf("ai", "ei")

    private val consonantVowel = +concat(onset, vowels)
    private val consonantSafeDiph = +concat(onset, safeDiph)

    private val endCons = anyOf("n", "m", "l", "r", "s")

    private val surnameInitialSyllable = +concat(initialSet, vowels)
    private val surnameMiddleSyllable =
        anyOf(consonantVowel, consonantSafeDiph)
    private val surnameFinalSyllable =
        +concat(anyOf(consonantVowel, consonantSafeDiph), endCons)

    private val givenMiddleSyllable =
        +((0..1) * anyOf(consonantVowel, consonantSafeDiph))
    private val givenFinalSyllable =
        consonantVowel

    private val additionPrefix = +concat(onset, vowels)
    private val additionSuffix = endCons

    private val surnameGenerator =
        (
                +concat(
                    surnameInitialSyllable,
                    +((0..2) * surnameMiddleSyllable),
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
            FictionalName(addendum = it)
        }

    private val fullNameParts = listOf(
        givenNameGenerator(),
        givenNameGenerator(),
        additionGenerator,
        surnameGenerator
    )

    private fun String.firstLetterUpperCase(): String =
        this[0].uppercase() + substring(1).lowercase()
}
