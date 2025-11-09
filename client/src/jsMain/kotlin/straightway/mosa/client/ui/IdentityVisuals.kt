package straightway.mosa.client.ui

import straightway.mosa.client.avatar.DiceBearAvatarGenerator
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.util.Base64
import straightway.mosa.client.util.Base64.toBase64Url
import straightway.mosa.identity.FictionalName
import straightway.mosa.identity.FictionalNameGenerator
import straightway.mosa.util.BitStream

data class IdentityVisuals(
    val name: FictionalName,
    val avatar: String,
)

class IdentityVisualsFactory(
    private val nameBuilder: (BitStream) -> FictionalName,
    private val avatarBuilder: (String) -> String,
) {
    constructor(
        nameGenerator: FictionalNameGenerator,
        avatarGenerator: DiceBearAvatarGenerator,
    ) : this(nameGenerator::generate, avatarGenerator::dataUri)

    fun create(identity: Identity): IdentityVisuals {
        val gidBytes = Base64.fromBase64Url(identity.gid)
        val bits = BitStream(gidBytes)
        val name = nameBuilder(bits)
        val avatarSeed = bits.nextBytes(8).toBase64Url()
        val avatar = avatarBuilder(avatarSeed)
        return IdentityVisuals(name = name, avatar = avatar)
    }
}
