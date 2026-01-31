package straightway.mosa.commandline

object CommandLineOptionsLogging {
    fun Map<String, Any?>.commandLineOptionsLogString(heading: String? = null) =
        buildString {
            heading?.let {
                appendLine(heading)
            }
            val maxKeyLength = keys.maxOfOrNull { it.length } ?: 0
            toSortedMap().forEach { (key, value) ->
                append("  ")
                append(key.padEnd(maxKeyLength))
                append(" = ")
                appendLine(value.toString())
            }
        }
}
