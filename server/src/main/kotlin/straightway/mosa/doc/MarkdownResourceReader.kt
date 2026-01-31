package straightway.mosa.doc

import straightway.mosa.commandline.ServeCommand

object MarkdownResourceReader {
    fun readMarkdownResource(path: String): String {
        val content =
            ServeCommand::class.java
                .getResourceAsStream(path)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return "Missing serve documentation resource: ${path.removePrefix("/")}"
        return expandMarkdownIncludes(content)
    }

    private fun expandMarkdownIncludes(markdown: String): String {
        val includePattern = Regex("\\{\\{include:([^}]+)}}")
        return buildString {
            markdown.lineSequence().forEach { line ->
                val match = includePattern.matchEntire(line.trim())
                if (match == null) {
                    appendLine(line)
                } else {
                    val includePath = match.groupValues[1].trim()
                    appendLine(readMarkdownResource(includePath))
                }
            }
        }.trimEnd()
    }
}
