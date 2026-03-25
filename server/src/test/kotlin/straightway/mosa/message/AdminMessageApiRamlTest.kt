package straightway.mosa.message

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class AdminMessageApiRamlTest {
    @Test
    fun `raml describes admin inbox incremental sync semantics`() {
        val raml = Files.readString(findRepoRoot().resolve("spec/mosa-pod-api.raml"))

        assertTrue(raml.contains("The time boundary is inclusive so the client can"))
        assertTrue(raml.contains("Older visible entries outside the requested window are not marked as retrieved by that refresh."))
        assertTrue(raml.contains("Use `0` to request the full currently visible inbox."))
        assertTrue(raml.contains("Pagination applies within the filtered result window."))
    }

    private fun findRepoRoot(): Path {
        var current = Path.of("").toAbsolutePath()
        while (current.fileName != null) {
            if (Files.exists(current.resolve("spec/mosa-pod-api.raml"))) {
                return current
            }
            current = current.parent ?: break
        }
        error("Repository root with spec/mosa-pod-api.raml not found")
    }
}
