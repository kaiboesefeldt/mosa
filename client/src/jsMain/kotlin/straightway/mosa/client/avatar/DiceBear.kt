package straightway.mosa.client.avatar

import kotlin.js.json

@JsModule("@dicebear/core")
@JsNonModule
external object DiceBearCore {
    fun createAvatar(style: DiceBearStyle, options: DiceBearOptions = definedExternally): DiceBearAvatar
}

external interface DiceBearAvatar {
    fun toDataUri(): String
}

external interface DiceBearOptions {
    var seed: String?
}

external interface DiceBearStyle {
}

class DiceBearAvatarGenerator(
    private val style: DiceBearStyle = AdventurerNeutralStyle
) {
    fun dataUri(seed: String): String {
        val options = json("seed" to seed).unsafeCast<DiceBearOptions>()
        return DiceBearCore.createAvatar(style, options).toDataUri()
    }
}

@JsModule("@dicebear/adventurer-neutral")
@JsNonModule
external object AdventurerNeutralStyle : DiceBearStyle
