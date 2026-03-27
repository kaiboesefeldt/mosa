package straightway.mosa.legal.clearterms.spi.browser

import straightway.mosa.client.util.textEncode
import straightway.mosa.legal.clearterms.spi.TextEncoder

class BrowserTextEncoder : TextEncoder {
    override fun encode(value: String): ByteArray = textEncode(value)
}
