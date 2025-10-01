package straightway.mosa.identity

import straightway.mosa.util.BitStream
import straightway.mosa.util.CombiningTextGenerator
import straightway.mosa.util.FixedOptionsTextGenerator
import straightway.mosa.util.RepeatingTextGenerator
import straightway.mosa.util.SelectingTextGenerator
import straightway.mosa.util.TextGenerator

class FictionalNameGenerator : TextGenerator {

    override val numberOfPossibleOutputs get() = sureNameGenerator.numberOfPossibleOutputs
    override val allPossibleOutputs get() = sureNameGenerator.allPossibleOutputs
    override val bitConsumption get() = sureNameGenerator.bitConsumption

    override fun generate(bits: BitStream): String {
        return sureNameGenerator.generate(bits).let {
            it[0].uppercase() + it.substring(1).lowercase()
        }
    }

    private val shortVowels = setOf("a", "e", "i", "o", "u", "y")
    private val longVowels = setOf("aa", "ai", "ee", "ei", "ie", "oo")

    private val universalConsonants = setOf(
        "b", "c", "d", "f", "g", "k", "l",
        "m", "n", "p", "r", "s", "t", "v", "w"
    )

    private val shorteningConsonants = setOf(
        "ck", "ch", "dd", "ff", "gg", "kk", "ll", "mm", "nn", "pp", "rr", "rd", "rt", "ss", "sh", "st",
        "tt", "x", "z"
    )

    private val initialConsonants = setOf(
        "h", "j", "gh", "qu", "sp", "sk"
    )

    private val endShorteningConsonants = setOf(
        "ck", "ch", "ll", "mm", "mp", "nn", "pp", "rr", "rd", "rg", "rk", "rs", "rt", "ss",
        "sh", "sk", "st", "sp", "tt", "x", "z"
    )

    private val shortVowelGenerator = FixedOptionsTextGenerator(shortVowels.sorted())
    private val longVowelGenerator = FixedOptionsTextGenerator(longVowels.sorted())

    private val initialConsonantGenerator =
        FixedOptionsTextGenerator((universalConsonants + initialConsonants).sorted())

    private val nonShorteningConsonantGenerator =
        FixedOptionsTextGenerator((universalConsonants).sorted())

    private val shorteningConsonantGenerator =
        FixedOptionsTextGenerator((universalConsonants + shorteningConsonants).sorted())

    private val endUniversalConsonantGenerator =
        FixedOptionsTextGenerator((universalConsonants).sorted())

    private val endShorteningConsonantGenerator =
        FixedOptionsTextGenerator((endShorteningConsonants + universalConsonants).sorted())

    private val initialSyllableGenerator = SelectingTextGenerator(
        1 to CombiningTextGenerator(
            shortVowelGenerator,
            shorteningConsonantGenerator
        ),
        1 to initialConsonantGenerator
    )

    private val middleSyllableGenerator =
        SelectingTextGenerator(
            5 to CombiningTextGenerator(
                shortVowelGenerator,
                shorteningConsonantGenerator
            ),
            1 to CombiningTextGenerator(
                longVowelGenerator,
                nonShorteningConsonantGenerator
            ),
            2 to CombiningTextGenerator()
        )

    private val endSyllableGenerator = SelectingTextGenerator(
        5 to CombiningTextGenerator(
            shortVowelGenerator,
            endShorteningConsonantGenerator
        ),
        1 to CombiningTextGenerator(
            longVowelGenerator,
            endUniversalConsonantGenerator
        ),
        2 to shortVowelGenerator
    )

    private val sureNameGenerator = CombiningTextGenerator(
        initialSyllableGenerator,
        RepeatingTextGenerator(middleSyllableGenerator, repetitions = 0..3),
        endSyllableGenerator
    )
}