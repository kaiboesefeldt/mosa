package straightway.mosa.legal.clearterms

import straightway.mosa.client.util.textEncode

class ClearTermsBrowserTextEncoder : ClearTermsTextEncoder {
    override fun encode(value: String): ByteArray = textEncode(value)
}
