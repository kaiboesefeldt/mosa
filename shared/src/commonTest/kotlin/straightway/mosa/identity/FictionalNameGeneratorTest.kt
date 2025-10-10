package straightway.mosa.identity

import straightway.mosa.util.bitsRequiredForMax
import kotlin.collections.LinkedHashSet
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class FictionalNameGeneratorTest {
    @Test
    fun `smoke test`() {
        val sut = FictionalNameGenerator()
        val rng = Random(12345)

        val sampleIndices = sampleUniqueIndices(sut.size, 2_000, rng)
        val sampleNames = sampleIndices.map(sut::get)

        assertEquals(sampleIndices.size, sampleNames.size, "No name duplicates")

        println("Overall size: ${formatNumber(sut.size)} Names (${bitsRequiredForMax(sut.size)} Bits)")
        val normalNamesCount = 50
        println((sampleNames.take(normalNamesCount)).joinToString("\n") { "- $it" })
        println((sampleNames.drop(normalNamesCount).filter(::hasDiphtong).take(5)).joinToString("\n") { "- $it" })
        println((sampleNames.drop(normalNamesCount).filter(::isShort).take(5)).joinToString("\n") { "- $it" })
    }

    private fun sampleUniqueIndices(
        generatorSize: Long,
        targetCount: Int,
        rng: Random,
    ): List<Long> {
        val indices = LinkedHashSet<Long>()
        while (indices.size < targetCount) {
            indices += rng.nextLong(generatorSize)
        }
        return indices.toList()
    }

    private fun formatNumber(n: Long): String =
        n
            .toString()
            .reversed()
            .mapIndexed { i, c -> if ((i + 1) % 3 == 0) "_$c" else "$c" }
            .reversed()
            .joinToString("")
            .replace(Regex("^_"), "")

    private fun hasDiphtong(n: FictionalName): Boolean = Regex("[aeiou]{2}").find(n.toString()) != null

    private fun isShort(n: FictionalName): Boolean = n.sureName.count { it in setOf('a', 'e', 'i', 'o', 'u') } <= 2
}
