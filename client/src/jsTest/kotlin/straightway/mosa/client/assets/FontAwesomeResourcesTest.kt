package straightway.mosa.client.assets

import kotlin.test.Test

class FontAwesomeResourcesTest {
    @Test
    fun `does not fail while loading styles`() {
        FontAwesomeResources.loadStyles()
    }
}
