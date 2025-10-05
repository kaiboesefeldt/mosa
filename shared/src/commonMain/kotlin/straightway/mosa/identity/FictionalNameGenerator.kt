package straightway.mosa.identity

import straightway.mosa.util.CombiningTextGenerator
import straightway.mosa.util.TextGenerator
import straightway.mosa.util.anyOf
import straightway.mosa.util.combine
import straightway.mosa.util.times
import straightway.mosa.util.div
import straightway.mosa.util.flat
import straightway.mosa.util.plus

class FictionalNameGenerator : TextGenerator.Base() {

    override val size get() = fullNameGenerator.size
    override operator fun get(index: Long) = fullNameGenerator[index]

    private val onset = anyOf(
        "p", "b", "t", "d", "k", "g", "f", "s", "z", "h", "m", "n", "l", "r", "v", "w",
        //"pr", "dr", "gr", "fr",
        //"pl", "bl", "fl"
    )
    private val v = anyOf("a", "e", "i", "o", "u")
    private val dt = anyOf("au", "ai", "ei", "oi")
    private val cv = flat(onset + v)
    private val cdt = flat(onset + dt)

    private val codac = anyOf("n", "m", "r", "l", "s", "r", "ng")

    private val sMiddleSyl =
        ((1..2) * cv) / combine(cdt, cv)
    private val gMiddleSyl =
        ((0..1) * (cv / cdt))

    private val sFinalSyl = cv + codac
    private val gFinalSyl = (cv / cdt)

    private val aPre = anyOf("za", "qi", "vo", "ka", "lu", "ze", "tra")
    private val aFin = anyOf("q", "x", "th", "zh")

    private val sureNameGenerator = (sMiddleSyl + sFinalSyl).joinBy { it.joinToString("").firstLetterUpperCase() }
    private val givenName1Generator = (v + gFinalSyl).joinBy { it.joinToString("").firstLetterUpperCase() }
    private val givenName2Generator = (v + gMiddleSyl + gFinalSyl).joinBy { it.joinToString("").firstLetterUpperCase() }
    private val additionGenerator = (aPre + aFin + "'")

    private val fullNameGenerator = CombiningTextGenerator(listOf(givenName1Generator, givenName2Generator, additionGenerator, sureNameGenerator)).joinBy { it.joinToString(" ").replace("' ", "'").replace("  ", " ") }

    private fun String.firstLetterUpperCase(): String =
        this[0].uppercase() + this.substring(1).lowercase()
}