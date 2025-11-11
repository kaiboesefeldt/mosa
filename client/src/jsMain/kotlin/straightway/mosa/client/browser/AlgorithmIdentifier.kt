package straightway.mosa.client.browser

import kotlin.js.unsafeCast

external interface AlgorithmIdentifier

object AlgorithmIdentifiers {
    val ed25519: AlgorithmIdentifier
        get() = js("({ name: 'Ed25519' })").unsafeCast<AlgorithmIdentifier>()
}
