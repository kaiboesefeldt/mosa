package straightway.mosa.legal.clearterms.render

import straightway.mosa.legal.clearterms.document.TextBlock
import straightway.mosa.legal.clearterms.document.TextOverrideSeverity
import straightway.mosa.legal.clearterms.render.RenderBackground

object BlockBackgroundResolver {
    fun resolve(
        blockId: String,
        blockOverrideCount: Map<String, Int>,
        baselineBlocksById: Map<String, TextBlock>,
        baselineBlockIds: Set<String>,
    ): RenderBackground {
        val count = blockOverrideCount[blockId] ?: 0
        if (blockId !in baselineBlockIds && count > 0) {
            return RenderBackground.RED
        }
        if (count == 0) {
            return RenderBackground.NONE
        }
        val baselineBlock = baselineBlocksById[blockId] ?: return RenderBackground.NONE
        return backgroundForSeverity(resolveSeverity(baselineBlock), count)
    }

    private fun resolveSeverity(block: TextBlock): TextOverrideSeverity = block.hints?.override ?: defaultSeverity(block)

    private fun defaultSeverity(block: TextBlock): TextOverrideSeverity =
        if (block.text == null) {
            TextOverrideSeverity.ONCE
        } else {
            TextOverrideSeverity.ALERT
        }

    private fun backgroundForSeverity(
        severity: TextOverrideSeverity,
        count: Int,
    ): RenderBackground =
        when (severity) {
            TextOverrideSeverity.ALERT -> {
                RenderBackground.RED
            }

            TextOverrideSeverity.WARN -> {
                RenderBackground.YELLOW
            }

            TextOverrideSeverity.ONCE -> {
                if (count > 1) {
                    RenderBackground.RED
                } else {
                    RenderBackground.NONE
                }
            }

            TextOverrideSeverity.OK -> {
                RenderBackground.NONE
            }
        }
}
