package straightway.mosa.identity

import straightway.mosa.util.BitStream
import kotlin.io.encoding.Base64

object IdentityLogEntryFormatter {
    private val nameGenerator = FictionalNameGenerator()

    fun format(gid: String): String {
        val name = gid.toFictionalNameOrNull() ?: return gid
        return "$gid ($name)"
    }

    private fun String.toFictionalNameOrNull(): String? {
        val bytes =
            runCatching {
                Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(this)
            }.getOrNull()
                ?: return null
        return nameGenerator.generate(BitStream(bytes)).toString()
    }
}
