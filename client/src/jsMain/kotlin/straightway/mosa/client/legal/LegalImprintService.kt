package straightway.mosa.client.legal

import straightway.mosa.client.content.PodAccessFactory
import straightway.mosa.client.pod.PodBaseProvider

class LegalImprintService(
    private val podBaseProvider: PodBaseProvider,
    private val podAccessFactory: PodAccessFactory,
    private val imprintHtmlRenderer: ImprintHtmlRenderer = ImprintHtmlRenderer(),
    private val podBaseResolver: () -> String? = { podBaseProvider.base() },
) {
    suspend fun legalInformationComplete(): Boolean {
        val podBase = podBaseResolver() ?: throw IllegalStateException("No pod base available")
        return podAccessFactory(podBase).legalStatus().complete
    }

    suspend fun loadImprintDocument(): LegalPrivacyPolicyDocument {
        val podBase = podBaseResolver() ?: throw IllegalStateException("No pod base available")
        val document = podAccessFactory(podBase).imprint()
        return LegalPrivacyPolicyDocument(
            html = imprintHtmlRenderer.render(document),
            uncriticalTextByBlockId = emptyMap(),
        )
    }
}
