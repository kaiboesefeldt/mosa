package straightway.mosa.crypto

import java.security.PublicKey
import kotlin.io.encoding.Base64

fun PublicKey.toBase64(): String =
    Base64.Default.UrlSafe
        .withPadding(Base64.PaddingOption.ABSENT)
        .encode(this.encoded.takeLast(32).toByteArray())
