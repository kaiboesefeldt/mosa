@file:Suppress("ktlint:standard:no-wildcard-imports")

package straightway.mosa.legal.clearterms

import straightway.mosa.legal.clearterms.document.*
import straightway.mosa.legal.clearterms.locale.*
import straightway.mosa.legal.clearterms.render.*
import straightway.mosa.legal.clearterms.render.BlockBackgroundResolver
import straightway.mosa.legal.clearterms.spi.*
import straightway.mosa.legal.clearterms.validation.*
import straightway.mosa.legal.clearterms.value.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ClearTermsBlockBackgroundResolverTest {
    @Test
    fun `marks appended overridden block as red`() {
        val background =
            BlockBackgroundResolver.resolve(
                blockId = "appended",
                blockOverrideCount = mapOf("appended" to 1),
                baselineBlocksById = emptyMap<String, TextBlock>(),
                baselineBlockIds = setOf("baseline"),
            )

        assertEquals(RenderBackground.RED, background)
    }

    @Test
    fun `marks warn override as yellow`() {
        val background =
            BlockBackgroundResolver.resolve(
                blockId = "main",
                blockOverrideCount = mapOf("main" to 1),
                baselineBlocksById =
                    mapOf(
                        "main" to
                            TextBlock(
                                id = "main",
                                text = "Baseline",
                                hints = TextBlockHints(expectedContent = null, override = TextOverrideSeverity.WARN),
                            ),
                    ),
                baselineBlockIds = setOf("main"),
            )

        assertEquals(RenderBackground.YELLOW, background)
    }

    @Test
    fun `keeps first once override uncolored`() {
        val background =
            BlockBackgroundResolver.resolve(
                blockId = "main",
                blockOverrideCount = mapOf("main" to 1),
                baselineBlocksById =
                    mapOf(
                        "main" to
                            TextBlock(
                                id = "main",
                                text = null,
                            ),
                    ),
                baselineBlockIds = setOf("main"),
            )

        assertEquals(RenderBackground.NONE, background)
    }
}
