package straightway.mosa.identity

import straightway.mosa.util.BitStream
import straightway.mosa.util.Generator
import straightway.mosa.util.anyOf
import straightway.mosa.util.bitsRequiredForMax
import straightway.mosa.util.concat
import straightway.mosa.util.trans
import straightway.mosa.util.unaryPlus

class FictionalNameGenerator {
    val size: Double
        get() = fullNameGenerator.size.toDouble()

    val bits: Int
        get() = bitsRequiredForMax(fullNameGenerator.size)

    fun generate(bits: BitStream): FictionalName = fullNameGenerator.generateFrom(bits)

    private fun Generator<FictionalName>.generateFrom(bits: BitStream): FictionalName {
        val requiredBits = bitsRequiredForMax(size)
        val nextIndex = bits.nextBitsLong(requiredBits)
        return this[nextIndex % size]
    }

    private val initSetGroupA = anyOf("b", "d", "dr", "g", "k", "m", "p", "st", "sp", "t")
    private val initSetGroupB = anyOf("br", "bl", "f", "fr", "fl", "kr", "kl", "l", "n", "pr", "r", "s", "sh")

    private val onsetGroupA = anyOf("b", "d", "g", "k", "m", "p", "t")
    private val onsetGroupB = anyOf("f", "l", "n", "r", "s")

    private val vowelsGroupA = anyOf("e", "i")
    private val vowelsGroupB = anyOf("a", "o", "u")

    private val initSyllableAA = +concat(initSetGroupA, vowelsGroupA)
    private val initSyllableAB = +concat(initSetGroupA, vowelsGroupB)
    private val initSyllableBA = +concat(initSetGroupB, vowelsGroupA)
    private val initSyllableBB = +concat(initSetGroupB, vowelsGroupB)

    private val syllableAA = +concat(onsetGroupA, vowelsGroupA)
    private val syllableBB = +concat(onsetGroupB, vowelsGroupB)
    private val syllableAB = +concat(onsetGroupA, vowelsGroupB)
    private val syllableBA = +concat(onsetGroupB, vowelsGroupA)

    private val endConsA = anyOf("b", "m", "t")
    private val endConsB = anyOf("l", "n", "ng", "r", "s")

    private fun surnameGenerator() =
        anyOf(
            +concat(initSyllableBB, syllableAA, syllableBB, endConsA),
            +concat(initSyllableAA, syllableBB, syllableAA, endConsB),
            +concat(initSyllableAB, syllableBA, syllableAB, endConsB),
            +concat(initSyllableBA, syllableAB, syllableBA, endConsA),
        ).trans { FictionalName(sureName = it.firstLetterUpperCase()) }

    private fun givenNameGenerator(syllableGen: Generator<String>): Generator<FictionalName> =
        syllableGen.trans {
            FictionalName(givenNames = listOf(it.firstLetterUpperCase()))
        }

    private val givenNameTwoSyllables =
        anyOf(
            +concat(initSyllableBB, syllableAA),
            +concat(initSyllableAB, syllableBA),
        )
    private val givenNameThreeSyllables =
        anyOf(
            +concat(initSyllableAA, syllableBB, syllableAA),
            +concat(initSyllableBA, syllableAB, syllableBA),
        )

    private val additionGenerator =
        (+concat(syllableBA, endConsA)).trans { FictionalName(addendum = it) }

    private val fullNameGenerator =
        anyOf(
            concat(
                anyOf(
                    givenNameGenerator(givenNameThreeSyllables),
                    givenNameGenerator(givenNameTwoSyllables),
                ),
                surnameGenerator(),
            ) { mergeParts(it) },
            concat(
                givenNameGenerator(givenNameTwoSyllables),
                additionGenerator,
                surnameGenerator(),
            ) { mergeParts(it) },
        )

    private fun mergeParts(parts: List<FictionalName>): FictionalName = parts.fold(FictionalName()) { acc, i -> acc + i }

    private fun String.firstLetterUpperCase(): String = this[0].uppercase() + substring(1).lowercase()
}
