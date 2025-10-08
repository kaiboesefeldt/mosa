package straightway.mosa.identity

import straightway.mosa.util.BaseGenerator
import straightway.mosa.util.CombiningGenerator
import straightway.mosa.util.anyOf
import straightway.mosa.util.concat
import straightway.mosa.util.times
import straightway.mosa.util.trans
import straightway.mosa.util.unaryPlus

class FictionalNameGenerator : BaseGenerator<FictionalName>() {

    override val size get() = fullNameGenerator.size
    override operator fun get(index: Long) = fullNameGenerator[index]

    private val initset = anyOf("ch", "b", "d", "f", "g", "h", "j", "k", "p", "r", "t", "v", "sh", "z", "zh")
    private val onset = anyOf("b", "d", "f", "l", "m", "n", "w", "s")
    private val v = anyOf("a", "e", "i", "o", "u")
    private val dt = anyOf("au", "ai", "ei", "oi")
    private val cv = +concat(onset, v)
    private val cdt = +concat(onset , dt)

    private val codac = anyOf("g", "n", "m", "r", "l", "s", "r", "ng")

    private val sInitSyl = +concat(initset, v)
    private val sMiddleSyl = anyOf(cv, cdt)
    private val gMiddleSyl = +((0..1) * (anyOf(cv, cdt)))

    private val sFinalSyl = anyOf(
        +concat(cv, codac),
        +concat(onset, anyOf("y")),
        codac
    )
    private val gFinalSyl = cv

    private val aPre = anyOf("y", "za", "qi", "vo", "ka", "lu", "ze", "tra")
    private val aFin = anyOf("q", "x", "th", "c")

    private val sureNameGenerator = (+concat(sInitSyl, +((0..1) * sMiddleSyl), sFinalSyl)).trans {
        FictionalName(sureName = it.firstLetterUpperCase())
    }
    private val givenName1Generator = (+concat(v, +((0..1) * gMiddleSyl), gFinalSyl)).trans {
        FictionalName(givenNames = listOf(it.firstLetterUpperCase()))
    }
    private val givenName2Generator = (+concat(v, +((0..1) * gMiddleSyl), gFinalSyl)).trans {
        FictionalName(givenNames = listOf(it.firstLetterUpperCase()))
    }
    private val additionGenerator = (+concat(aPre, aFin)).trans {
        FictionalName(addendum = it)
    }

    private val fullNameGenerator = CombiningGenerator(listOf(givenName1Generator, givenName2Generator, additionGenerator, sureNameGenerator)).trans {
        it.fold(FictionalName()) { acc, i ->
            acc.copy(
                givenNames = acc.givenNames + i.givenNames,
                sureName =  acc.sureName + i.sureName,
                addendum = when {
                    acc.addendum == null -> i.addendum
                    i.addendum == null -> acc.addendum
                    else -> i.addendum + acc.addendum
                }
            )
        }
    }

    private fun String.firstLetterUpperCase(): String =
        this[0].uppercase() + substring(1).lowercase()
}