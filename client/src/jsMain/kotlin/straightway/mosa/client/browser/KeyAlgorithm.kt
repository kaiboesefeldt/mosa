package straightway.mosa.client.browser

import kotlin.js.json

sealed interface KeyAlgorithm {
    val descriptor: dynamic

    data object Ed25519 : KeyAlgorithm {
        override val descriptor: dynamic = json("name" to "Ed25519")
    }
}
