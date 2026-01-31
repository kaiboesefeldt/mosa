package straightway.mosa

fun interface CommandLineOptions {
    fun toArgumentValues(): Map<String, Any?>
}
