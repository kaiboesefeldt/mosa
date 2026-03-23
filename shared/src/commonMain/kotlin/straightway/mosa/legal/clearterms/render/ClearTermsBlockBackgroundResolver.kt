package straightway.mosa.legal.clearterms.render

import straightway.mosa.legal.clearterms.ClearTermsRenderBackground
import straightway.mosa.legal.clearterms.ClearTermsTextBlock
import straightway.mosa.legal.clearterms.ClearTermsTextOverrideSeverity

object ClearTermsBlockBackgroundResolver {
    fun resolve(
        blockId: String,
        blockOverrideCount: Map<String, Int>,
        baselineBlocksById: Map<String, ClearTermsTextBlock>,
        baselineBlockIds: Set<String>,
    ): ClearTermsRenderBackground {
        val count = blockOverrideCount[blockId] ?: 0
        if (blockId !in baselineBlockIds && count > 0) {
            return ClearTermsRenderBackground.RED
        }
        if (count == 0) {
            return ClearTermsRenderBackground.NONE
        }
        val baselineBlock = baselineBlocksById[blockId] ?: return ClearTermsRenderBackground.NONE
        return backgroundForSeverity(resolveSeverity(baselineBlock), count)
    }

    private fun resolveSeverity(block: ClearTermsTextBlock): ClearTermsTextOverrideSeverity =
        block.hints?.override ?: defaultSeverity(block)

    private fun defaultSeverity(block: ClearTermsTextBlock): ClearTermsTextOverrideSeverity =
        if (block.text == null) {
            ClearTermsTextOverrideSeverity.ONCE
        } else {
            ClearTermsTextOverrideSeverity.ALERT
        }

    private fun backgroundForSeverity(
        severity: ClearTermsTextOverrideSeverity,
        count: Int,
    ): ClearTermsRenderBackground =
        when (severity) {
            ClearTermsTextOverrideSeverity.ALERT -> {
                ClearTermsRenderBackground.RED
            }

            ClearTermsTextOverrideSeverity.WARN -> {
                ClearTermsRenderBackground.YELLOW
            }

            ClearTermsTextOverrideSeverity.ONCE -> {
                if (count > 1) {
                    ClearTermsRenderBackground.RED
                } else {
                    ClearTermsRenderBackground.NONE
                }
            }

            ClearTermsTextOverrideSeverity.OK -> {
                ClearTermsRenderBackground.NONE
            }
        }
}
