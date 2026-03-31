package straightway.mosa.client.ui

import straightway.mosa.client.i18n.Translator

interface IdentitySessionTextProvider {
    val loading: String
    val unknownError: String
    val invalidIdentity: String
    val exportInProgress: String
    val exportError: String
    val deleteError: String
    val freezeInProgress: String
    val freezeSuccess: String
    val freezeError: String
    val restoreInProgress: String
    val restoreError: String
    fun failedToPrepare(message: String): String
    fun restoreSuccess(restoredCount: Int): String
}

class TranslatorIdentitySessionTextProvider(
    private val translator: Translator,
) : IdentitySessionTextProvider {
    override val loading: String = translator.translate("identity.loading")
    override val unknownError: String = translator.translate("identity.unknownError")
    override val invalidIdentity: String = translator.translate("identity.invalidIdentity")
    override val exportInProgress: String = translator.translate("identity.export.inProgress")
    override val exportError: String = translator.translate("identity.export.error")
    override val deleteError: String = translator.translate("identity.delete.error")
    override val freezeInProgress: String = translator.translate("identity.snapshot.freeze.inProgress")
    override val freezeSuccess: String = translator.translate("identity.snapshot.freeze.success")
    override val freezeError: String = translator.translate("identity.snapshot.freeze.error")
    override val restoreInProgress: String = translator.translate("identity.snapshot.restore.inProgress")
    override val restoreError: String = translator.translate("identity.snapshot.restore.error")

    override fun failedToPrepare(message: String): String =
        translator.translate("identity.failedToPrepare", mapOf("message" to message))

    override fun restoreSuccess(restoredCount: Int): String =
        translator.translate("identity.snapshot.restore.success", mapOf("count" to restoredCount.toString()))
}
