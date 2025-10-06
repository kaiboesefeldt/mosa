package straightway.mosa.identity

import straightway.mosa.util.BaseGenerator
import straightway.mosa.util.CombiningGenerator
import straightway.mosa.util.Generator
import straightway.mosa.util.anyOf
import straightway.mosa.util.combine
import straightway.mosa.util.concat
import straightway.mosa.util.times
import straightway.mosa.util.flat
import straightway.mosa.util.stringGen
import straightway.mosa.util.trans

class FictionalNameGenerator : BaseGenerator<String>() {

    override val size get() = fullNameGenerator.size
    override operator fun get(index: Long) = fullNameGenerator[index]

    private val onset = anyOf(
        "p", "b", "t", "d", "k", "g", "f", "s", "z", "h", "m", "n", "l", "r", "v", "w",
    )
    private val v = anyOf("a", "e", "i", "o", "u")
    private val dt = anyOf("au", "ai", "ei", "oi")
    private val cv = flat(concat(onset, v) { it.joinToString("") })
    private val cdt = flat(concat(onset , dt) { it.joinToString("") })

    private val codac = anyOf("n", "m", "r", "l", "s", "r", "ng")

    private val sMiddleSyl = stringGen(anyOf((1..2) * cv, combine(cdt, cv)))
    private val gMiddleSyl = stringGen(((0..1) * (anyOf(cv, cdt))))

    private val sFinalSyl = concat(cv, codac) { it.joinToString("") }
    private val gFinalSyl = anyOf(cv, cdt)

    private val aPre = anyOf("za", "qi", "vo", "ka", "lu", "ze", "tra")
    private val aFin = anyOf("q", "x", "th", "zh")

    private val sureNameGenerator = concat(sMiddleSyl, sFinalSyl).firstLetterUpperCase()
    private val givenName1Generator = concat(v, gFinalSyl).firstLetterUpperCase()
    private val givenName2Generator = concat(v, gMiddleSyl, gFinalSyl).firstLetterUpperCase()
    private val additionGenerator = concat(aPre, aFin, anyOf("'")) { it.joinToString("") }

    private val fullNameGenerator = CombiningGenerator(listOf(givenName1Generator, givenName2Generator, additionGenerator, sureNameGenerator)).trans { it.joinToString(" ").replace("' ", "'").replace("  ", " ") }

    private fun Generator<List<String>>.firstLetterUpperCase(): Generator<String> =
        trans { l -> l.joinToString("").let { it[0].uppercase() + it.substring(1).lowercase() } }
}