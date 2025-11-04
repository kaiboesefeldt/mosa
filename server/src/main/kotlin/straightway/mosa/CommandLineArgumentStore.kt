package straightway.mosa

fun interface CommandLineArgumentStore {
    fun get(name: String): Any?

    companion object {
        val EMPTY = from { null }

        fun from(provider: (String) -> Any?): CommandLineArgumentStore = CommandLineArgumentStore { name -> provider(name) }
    }
}
