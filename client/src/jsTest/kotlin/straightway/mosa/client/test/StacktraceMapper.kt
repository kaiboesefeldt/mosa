package straightway.mosa.client.test

import kotlinx.browser.window
import kotlin.js.JSON
import kotlin.js.JsName
import kotlin.js.Json
import kotlin.js.json
import org.w3c.xhr.XMLHttpRequest

@Suppress("unused")
@JsModule("source-map")
@JsNonModule
private external object SourceMapModule {
    @JsName("SourceMapConsumer")
    class SourceMapConsumer(sourceMap: dynamic) {
        fun originalPositionFor(query: Json): dynamic
    }
}

private object StacktraceMapper {
    private const val KARMA_HOOK_FLAG = "__mosaStacktraceHook__"
    private val framePattern = Regex("(.*@)?([^@:]+):(\\d+):(\\d+)")
    private val karmaPathPattern = Regex("(/tmp/_karma_webpack_\\d+/[^\\s:]+\\.js)")
    private val consumerCache = mutableMapOf<String, SourceMapModule.SourceMapConsumer?>()
    private var installed = false

    fun install() {
        if (installed) return
        val karma = window.asDynamic().__karma__
        if (karma == undefined) return
        if (karma[KARMA_HOOK_FLAG] === true) {
            installed = true
            return
        }
        val originalResult = karma.result
        karma.result = { result: dynamic ->
            val mappedLogs = mapLogs(result.log)
            if (mappedLogs != null) {
                result.log = mappedLogs
            }
            originalResult.call(karma, result)
        }
        karma[KARMA_HOOK_FLAG] = true
        installed = true
    }

    private fun mapLogs(logs: dynamic): Array<String>? {
        if (logs == null) {
            return null
        }
        val isArray = js("Array.isArray")(logs) as Boolean
        if (!isArray) {
            return null
        }
        val entries = logs.unsafeCast<Array<Any?>>()
        val mapped = entries.map { entry -> mapLogEntry(entry as? String) }
        return mapped.toTypedArray()
    }

    private fun mapLogEntry(entry: String?): String {
        if (entry == null) {
            return ""
        }
        return entry.split('\n').joinToString("\n") { frame -> mapFrame(frame) }
    }

    private fun mapFrame(frame: String): String {
        val match = framePattern.find(frame) ?: return frame
        val prefix = match.groupValues[1]
        val path = match.groupValues[2]
        val line = match.groupValues[3].toInt()
        val column = match.groupValues[4].toInt()
        val karmaUrl = toKarmaUrl(path) ?: return frame
        val consumer = consumerFor(karmaUrl) ?: return frame
        val query = json("line" to line, "column" to (column - 1))
        val position = consumer.originalPositionFor(query)
        val source = position.source as? String ?: return frame
        val mappedLine = position.line as? Int ?: return frame
        val mappedColumn = (position.column as? Int ?: 0) + 1
        val normalizedSource = source.removePrefix("webpack://")
        return "$prefix$normalizedSource:$mappedLine:$mappedColumn"
    }

    private fun toKarmaUrl(path: String): String? {
        val match = karmaPathPattern.find(path) ?: return null
        val relative = match.groupValues[1]
        val url = window.location.origin + "/absolute" + relative
        return url
    }

    private fun consumerFor(url: String): SourceMapModule.SourceMapConsumer? {
        if (consumerCache.containsKey(url)) {
            return consumerCache[url]
        }
        val map = loadSourceMap(url) ?: run {
            consumerCache[url] = null
            return null
        }
        val consumer = SourceMapModule.SourceMapConsumer(map)
        consumerCache[url] = consumer
        return consumer
    }

    private fun loadSourceMap(url: String): dynamic {
        val xhr = XMLHttpRequest()
        xhr.open("GET", url, false)
        try {
            xhr.send()
        } catch (_: dynamic) {
            return null
        }
        if (xhr.status.toInt() !in 200..299) {
            return null
        }
        val text = xhr.responseText
        val marker = "sourceMappingURL=data:application/json"
        val index = text.lastIndexOf(marker)
        if (index < 0) {
            return null
        }
        val payload = text.substring(index + marker.length)
        val encoded = payload
            .removePrefix(";charset=utf-8;base64,")
            .removePrefix(";base64,")
        val decoded = window.atob(encoded)
        return JSON.parse(decoded)
    }
}

internal fun ensureStacktraceMapperInstalled() {
    StacktraceMapper.install()
}
