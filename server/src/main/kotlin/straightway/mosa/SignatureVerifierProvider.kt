package straightway.mosa

import java.security.Signature

fun interface SignatureVerifierProvider {
    fun get(): Signature
}
