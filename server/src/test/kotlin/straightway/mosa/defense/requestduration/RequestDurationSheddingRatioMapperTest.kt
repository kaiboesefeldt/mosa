package straightway.mosa.defense.requestduration

import kotlin.test.Test
import kotlin.test.assertEquals

class RequestDurationSheddingRatioMapperTest {
    @Test
    fun `returns zero at slack threshold`() {
        val mapper = RequestDurationSheddingRatioMapper(RequestDurationSheddingConfig(ratioSlack = 0.1, fullSheddingRatio = 2.0))

        val factor = mapper.targetSheddingFactor(1.1)

        assertEquals(0.0, factor)
    }

    @Test
    fun `returns one at full shedding ratio`() {
        val mapper = RequestDurationSheddingRatioMapper(RequestDurationSheddingConfig(ratioSlack = 0.1, fullSheddingRatio = 2.0))

        val factor = mapper.targetSheddingFactor(2.0)

        assertEquals(1.0, factor)
    }

    @Test
    fun `returns zero for non finite ratio`() {
        val mapper = RequestDurationSheddingRatioMapper(RequestDurationSheddingConfig(ratioSlack = 0.1, fullSheddingRatio = 2.0))

        val factor = mapper.targetSheddingFactor(Double.NaN)

        assertEquals(0.0, factor)
    }

    @Test
    fun `returns zero for infinite ratio`() {
        val mapper = RequestDurationSheddingRatioMapper(RequestDurationSheddingConfig(ratioSlack = 0.1, fullSheddingRatio = 2.0))

        val factor = mapper.targetSheddingFactor(Double.POSITIVE_INFINITY)

        assertEquals(0.0, factor)
    }
}
