package straightway.mosa.client.identity

import straightway.mosa.client.browser.CryptoKeyPair

data class Identity(
    val gid: String,
    val keyPair: CryptoKeyPair,
    val protectedExport: String? = null,
)
