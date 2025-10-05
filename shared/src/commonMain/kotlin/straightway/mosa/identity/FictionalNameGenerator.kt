package straightway.mosa.identity

import straightway.mosa.util.BitStream
import straightway.mosa.util.CombiningTextGenerator
import straightway.mosa.util.TextGenerator
import straightway.mosa.util.anyOf
import straightway.mosa.util.combine
import straightway.mosa.util.times
import straightway.mosa.util.div
import straightway.mosa.util.flat
import straightway.mosa.util.plus

class FictionalNameGenerator : TextGenerator {

    override val numberOfPossibleOutputs get() = sureNameGenerator.numberOfPossibleOutputs
    override val allPossibleOutputs get() = sureNameGenerator.allPossibleOutputs
    override val bitConsumption get() = sureNameGenerator.bitConsumption

    override fun generate(bits: BitStream): String {
        return sureNameGenerator.generate(bits).let {
            it[0].uppercase() + it.substring(1).lowercase()
        }
    }

    private val onset = anyOf("p", "b", "t", "d", "k", "g", "f", "s", "z", "h", "m", "n", "l", "r", "w")
    private val v = anyOf("a", "e", "i", "o", "u")
    private val vj = anyOf("a", "e")
    private val vjOnset = anyOf("bj", "tj", "kj", "fj", "nj")
    private val vw = anyOf("a", "o", "u")
    private val vwOnset = anyOf("bw", "dw", "kw", "gw", "sw", "zw")
    private val cv = flat(onset + v)
    private val cjv = flat(vjOnset + vj)
    private val cwv = flat(vwOnset + vw)

    private val codac = anyOf("n", "m", "r", "l", "s")

    private val middleSyl =
        (
            combine(cv, cv, cjv)
                    / combine(cv, cjv)
                    / combine(cv, cv, cwv)
                    / combine(cv, cwv)
                    / ((1..2) * cv)
                    / cjv
                / cwv
        )


    private val sFinalSyl = cv + codac

    private val sureNameGenerator = CombiningTextGenerator(
        middleSyl,
        sFinalSyl
    )
}