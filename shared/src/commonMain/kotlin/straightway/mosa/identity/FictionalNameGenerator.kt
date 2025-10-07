package straightway.mosa.identity

import straightway.mosa.util.BaseGenerator
import straightway.mosa.util.CombiningGenerator
import straightway.mosa.util.anyOf
import straightway.mosa.util.combine
import straightway.mosa.util.concat
import straightway.mosa.util.times
import straightway.mosa.util.flat
import straightway.mosa.util.stringGen
import straightway.mosa.util.trans

class FictionalNameGenerator : BaseGenerator<FictionalName>() {

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

    private val sMiddleSyl = stringGen(anyOf((0..2) * cv, combine(cdt, cv)))
    private val gMiddleSyl = stringGen(((0..1) * (anyOf(cv, cdt))))

    private val sFinalSyl = concat(cv, codac) { it.joinToString("") }
    private val gFinalSyl = anyOf(cv, cdt)

    private val aPre = anyOf("za", "qi", "vo", "ka", "lu", "ze", "tra")
    private val aFin = anyOf("q", "x", "th", "zh")

    private val sureNameGenerator = stringGen(concat(sMiddleSyl, sFinalSyl)).trans {
        FictionalName(sureName = it.firstLetterUpperCase())
    }
    private val givenName1Generator = stringGen(concat(v, gFinalSyl)).trans {
        FictionalName(givenNames = listOf(it.firstLetterUpperCase()))
    }
    private val givenName2Generator = stringGen(concat(v, gMiddleSyl, gFinalSyl)).trans {
        FictionalName(givenNames = listOf(it.firstLetterUpperCase()))
    }
    private val additionGenerator = stringGen(concat(aPre, aFin)).trans {
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