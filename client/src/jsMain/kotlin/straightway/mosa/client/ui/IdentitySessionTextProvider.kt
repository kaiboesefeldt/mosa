package straightway.mosa.client.ui

import straightway.mosa.client.i18n.Translator

interface IdentitySessionTextProvider {
    val loading: String
    val unknownError: String
    val missingPod: String
    fun failedToPrepare(message: String): String
    fun previewResultsEmpty(topic: String): String
    fun previewResultsHeader(topic: String): String
}

class TranslatorIdentitySessionTextProvider(
    private val translator: Translator,
) : IdentitySessionTextProvider {
    override val loading: String = translator.translate("identity.loading")
    override val unknownError: String = translator.translate("identity.unknownError")
    override val missingPod: String = translator.translate("landing.noPodConfigured")

    override fun failedToPrepare(message: String): String =
        translator.translate("identity.failedToPrepare", mapOf("message" to message))

    override fun previewResultsEmpty(topic: String): String =
        translator.translate("preview.resultsEmpty", mapOf("topic" to topic))

    override fun previewResultsHeader(topic: String): String =
        translator.translate("preview.resultsHeader", mapOf("topic" to topic))
}
