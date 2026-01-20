package straightway.mosa.auth

import straightway.mosa.Crypto
import straightway.mosa.SignatureFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.KeyPair
import java.security.PublicKey
import java.security.Signature

class FileBackedEd25519Factory(
    private val crypto: Crypto,
    private val keyPath: Path,
) : SignatureFactory {
    private val keyPair: KeyPair = loadOrCreateKeyPair()

    override val publicKey: PublicKey get() = keyPair.public

    override fun newSigner(): Signature =
        Signature.getInstance("Ed25519").apply {
            initSign(keyPair.private)
        }

    private fun loadOrCreateKeyPair(): KeyPair {
        if (Files.exists(keyPath)) {
            val pem = Files.readString(keyPath, StandardCharsets.US_ASCII)
            return crypto.importKeyPairFromPem(pem)
        }

        val generated = crypto.generateKeyPair()
        val pem = crypto.exportPrivateKeyPairToPem(generated)
        keyPath.parent?.let { Files.createDirectories(it) }
        Files.writeString(keyPath, pem, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW)
        return generated
    }
}
