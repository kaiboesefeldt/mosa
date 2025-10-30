package straightway.mosa.client.avatar

import kotlin.test.Test
import kotlin.test.assertTrue

class DiceBearAvatarGeneratorTest {
    @Test
    fun `generates svg data uri for seed`() {
        val generator = DiceBearAvatarGenerator()

        val dataUri = generator.dataUri("Kai")

        assertTrue(dataUri.startsWith("data:image/svg+xml;utf8,"), "Expected SVG data URI but got $dataUri")
    }
}
