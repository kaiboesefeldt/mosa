package straightway.mosa.legal

import java.net.URL
import java.util.Enumeration

fun interface ResourceUrlsProvider {
    fun list(directoryPath: String): Enumeration<URL>
}
