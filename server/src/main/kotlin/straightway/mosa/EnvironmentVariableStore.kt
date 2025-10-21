package straightway.mosa

fun interface EnvironmentVariableStore {
    fun get(name: String): String?
}
