package straightway.mosa.doc

import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.rendering.Theme
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text

class ServeDocRenderer {
    private val docTheme =
        Theme(Theme.Default) {
            val styles = styles
            styles["markdown.h1"] = TextColors.brightBlue + TextStyles.bold
            styles["markdown.h2"] = TextColors.blue + TextStyles.bold
            styles["markdown.h3"] = TextColors.blue + TextStyles.italic
            styles["markdown.h4"] = TextColors.blue
            styles["markdown.h5"] = TextColors.magenta
            styles["markdown.h6"] = TextColors.magenta
            styles["markdown.h1.rule"] = TextColors.gray + TextStyles.dim
            styles["markdown.code.span"] = TextColors.gray
            styles["markdown.code.block"] = TextColors.gray
            styles["markdown.code.block.border"] = TextColors.gray
            styles["markdown.link.text"] = TextColors.blue + TextStyles.underline
            styles["markdown.link.destination"] = TextColors.gray + TextStyles.dim
            styles["markdown.emph"] = TextStyles.italic.style
            styles["markdown.strong"] = TextStyles.bold.style
            styles["markdown.stikethrough"] = TextStyles.strikethrough.style
            styles["markdown.table.header"] = TextColors.brightBlue + TextStyles.bold
            styles["markdown.table.body"] = TextColors.gray
            styles["markdown.task.checked"] = TextColors.green
            styles["markdown.task.unchecked"] = TextColors.gray
        }

    fun render(markdown: String) {
        val terminal = Terminal(theme = docTheme)
        val headingPattern = Regex("^(#{1,6})\\s+(.*)$")
        val buffer = StringBuilder()

        var hasOutput = false
        var lastWasBlank = false

        fun printBlankLine() {
            if (!lastWasBlank) {
                terminal.println()
                lastWasBlank = true
            }
        }

        fun markOutput() {
            hasOutput = true
            lastWasBlank = false
        }

        fun flushBuffer() {
            if (buffer.isEmpty()) {
                return
            }
            val content = buffer.toString().trimEnd()
            if (content.isNotBlank()) {
                terminal.println(Markdown(content))
                markOutput()
            }
            buffer.clear()
        }

        markdown.lineSequence().forEach { line ->
            val match = headingPattern.matchEntire(line)
            if (match == null) {
                buffer.appendLine(line)
                return@forEach
            }
            flushBuffer()
            if (hasOutput) {
                printBlankLine()
            }
            val level = match.groupValues[1].length
            val text = match.groupValues[2].trim()
            val style = terminal.theme.style("markdown.h$level", TextStyles.bold.style)
            when (level) {
                1 -> terminal.printH1(text, style)
                2 -> terminal.printH2(text, style)
                else -> terminal.println(Text(style(text), align = TextAlign.LEFT))
            }
            markOutput()
            printBlankLine()
        }
        flushBuffer()
    }

    private fun Terminal.printH1(
        text: String,
        style: TextStyle,
    ) {
        val upperText = text.uppercase()
        this.println(Text(style(upperText), align = TextAlign.LEFT))
        val ruleStyle = theme.style("markdown.h1.rule", TextStyles.dim.style)
        val rule = "=".repeat(upperText.length.coerceAtLeast(3))
        this.println(Text(ruleStyle(rule), align = TextAlign.LEFT))
    }

    private fun Terminal.printH2(
        text: String,
        style: TextStyle,
    ) {
        val upperText = text.uppercase()
        this.println(Text(style(upperText), align = TextAlign.LEFT))
    }
}
