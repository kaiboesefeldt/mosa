package straightway.mosa.legal.clearterms

import straightway.mosa.legal.clearterms.render.ClearTermsBlockBackgroundResolver
import kotlin.test.Test
import kotlin.test.assertEquals

class ClearTermsBlockBackgroundResolverTest {
    @Test
    fun `marks appended overridden block as red`() {
        val background =
            ClearTermsBlockBackgroundResolver.resolve(
                blockId = "appended",
                blockOverrideCount = mapOf("appended" to 1),
                baselineBlocksById = emptyMap<String, ClearTermsTextBlock>(),
                baselineBlockIds = setOf("baseline"),
            )

        assertEquals(ClearTermsRenderBackground.RED, background)
    }

    @Test
    fun `marks warn override as yellow`() {
        val background =
            ClearTermsBlockBackgroundResolver.resolve(
                blockId = "main",
                blockOverrideCount = mapOf("main" to 1),
                baselineBlocksById =
                    mapOf(
                        "main" to
                            ClearTermsTextBlock(
                                id = "main",
                                text = "Baseline",
                                hints = ClearTermsTextBlockHints(expectedContent = null, override = ClearTermsTextOverrideSeverity.WARN),
                            ),
                    ),
                baselineBlockIds = setOf("main"),
            )

        assertEquals(ClearTermsRenderBackground.YELLOW, background)
    }

    @Test
    fun `keeps first once override uncolored`() {
        val background =
            ClearTermsBlockBackgroundResolver.resolve(
                blockId = "main",
                blockOverrideCount = mapOf("main" to 1),
                baselineBlocksById =
                    mapOf(
                        "main" to
                            ClearTermsTextBlock(
                                id = "main",
                                text = null,
                            ),
                    ),
                baselineBlockIds = setOf("main"),
            )

        assertEquals(ClearTermsRenderBackground.NONE, background)
    }
}
