package straightway.mosa.auth

import straightway.mosa.SignatureVerifierProvider
import java.security.Signature

class ThreadLocalEd25519VerifierProvider : SignatureVerifierProvider {
    private val signatures = ThreadLocal.withInitial { Signature.getInstance("Ed25519") }

    override fun get(): Signature = signatures.get()
}
