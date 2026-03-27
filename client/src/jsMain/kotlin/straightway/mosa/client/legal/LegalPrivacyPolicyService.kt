package straightway.mosa.client.legal

import straightway.mosa.client.content.PodAccessFactory
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.legal.clearterms.parse.JsonParser
import straightway.mosa.legal.clearterms.locale.LocaleProvider
import straightway.mosa.legal.clearterms.locale.LocaleRepository
import straightway.mosa.legal.clearterms.placeholder.PlaceholderParser
import straightway.mosa.legal.clearterms.validation.ProfileValidator
import straightway.mosa.legal.clearterms.render.RenderedBlock
import straightway.mosa.legal.clearterms.render.TimeTextRenderer
import straightway.mosa.legal.clearterms.render.TimeZoneDisplayConverter
import straightway.mosa.legal.clearterms.render.ValueRenderer
import straightway.mosa.legal.clearterms.parse.DocumentReader
import straightway.mosa.client.legal.clearterms.browser.spi.BrowserResourceReader
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class LegalPrivacyPolicyService(
    private val podBaseProvider: PodBaseProvider,
    private val podAccessFactory: PodAccessFactory,
    private val preferredLanguageTagsProvider: () -> List<String>,
    private val clearTermsContentProvider: PrivacyClearTermsContentProvider,
    private val markdownToHtmlRenderer: MarkdownToHtmlRenderer = MarkdownToHtmlRenderer(),
    private val clearTermsLocaleProvider: LocaleProvider = LocaleRepository(BrowserResourceReader()),
    private val timeZoneDisplayConverter: TimeZoneDisplayConverter = TimeZoneDisplayConverter.NoOp,
) {
    private val clearTermsPlaceholderParser = PlaceholderParser()
    private val clearTermsProfileValidator = ProfileValidator(clearTermsPlaceholderParser)
    private val clearTermsValueRenderer =
        ValueRenderer(
            clearTermsPlaceholderParser,
            clearTermsLocaleProvider,
            timeTextRenderer = TimeTextRenderer(timeZoneDisplayConverter = timeZoneDisplayConverter),
        )
    private val clearTermsLanguageSelector =
        LegalPrivacyPolicyLanguageSelector(
            clearTermsJsonParser = JsonParser(),
            clearTermsDocumentReader = DocumentReader(),
            clearTermsProfileValidator = clearTermsProfileValidator,
        )
    private val clearTermsComparisonResolver =
        LegalPrivacyPolicyComparisonResolver(
            clearTermsContentProvider = clearTermsContentProvider,
            clearTermsJsonParser = JsonParser(),
            clearTermsDocumentReader = DocumentReader(),
            clearTermsPlaceholderParser = clearTermsPlaceholderParser,
            clearTermsProfileValidator = clearTermsProfileValidator,
            clearTermsValueRenderer = clearTermsValueRenderer,
        )
    private val htmlRenderer = LegalPrivacyPolicyHtmlRenderer(markdownToHtmlRenderer)

    suspend fun loadPrivacyPolicyHtml(): String {
        return loadPrivacyPolicyDocument().html
    }

    suspend fun loadPrivacyPolicyDocument(): LegalPrivacyPolicyDocument {
        return loadDocument { it.privacyClearTerms() }
    }

    suspend fun loadTermsDocument(): LegalPrivacyPolicyDocument {
        return loadDocument { it.termsClearTerms() }
    }

    private suspend fun loadDocument(
        chainLoader: suspend (straightway.mosa.client.pod.PodAccess) -> straightway.mosa.legal.LegalDocumentChainResponse,
    ): LegalPrivacyPolicyDocument {
        val podBase = podBaseProvider.base() ?: throw IllegalStateException("No pod base available")
        val podAccess = podAccessFactory(podBase)
        val chain = chainLoader(podAccess)
        val baseline = chain.baseline.toCanonicalJson()
        val profiles = chain.profiles.map { profile -> profile.toCanonicalJson() }
        val languageTag = clearTermsLanguageSelector.select(baseline, profiles, preferredLanguageTagsProvider())
        val renderResult = clearTermsContentProvider.renderedLanguage(baseline, profiles, languageTag)
        if (renderResult.blocks.isEmpty()) {
            return LegalPrivacyPolicyDocument(
                html = markdownToHtmlRenderer.render(renderResult.text),
                uncriticalTextByBlockId = emptyMap(),
            )
        }
        val uncriticalTextByBlockId =
            clearTermsComparisonResolver.resolve(
                languageTag = languageTag,
                baselineJson = baseline,
                profileJsons = profiles,
                renderedBlocks = renderResult.blocks,
            )
        return LegalPrivacyPolicyDocument(
            html = htmlRenderer.render(renderResult.blocks),
            uncriticalTextByBlockId = uncriticalTextByBlockId,
        )
    }

    private fun JsonObject.toCanonicalJson(): String = this["canonicalJson"]?.jsonPrimitive?.content ?: toString()

    companion object {
        const val MARKER_OPEN_BASELINE = "[[CTBG:DF]]"
        const val MARKER_OPEN_YELLOW = "[[CTBG:YL"
        const val MARKER_OPEN_RED = "[[CTBG:RD"
        const val MARKER_OPEN_SUFFIX = "]]"
        const val MARKER_CLOSE = "[[/CTBG]]"
        const val INLINE_STYLE_YELLOW = "background-color: #fef3c7; color: #78350f;"
        const val INLINE_STYLE_RED = "background-color: #fee2e2; color: #7f1d1d;"
    }
}
