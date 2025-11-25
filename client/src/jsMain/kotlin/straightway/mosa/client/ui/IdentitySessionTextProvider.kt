package straightway.mosa.client.ui

import straightway.mosa.client.i18n.Translator

interface IdentitySessionTextProvider {
    val loading: String
    val unknownError: String
    val invalidIdentity: String
    fun failedToPrepare(message: String): String
}

class TranslatorIdentitySessionTextProvider(
    private val translator: Translator,
) : IdentitySessionTextProvider {
    override val loading: String = translator.translate("identity.loading")
    override val unknownError: String = translator.translate("identity.unknownError")
    override val invalidIdentity: String = translator.translate("identity.invalidIdentity")

    override fun failedToPrepare(message: String): String =
        translator.translate("identity.failedToPrepare", mapOf("message" to message))
}
