package straightway.mosa.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class IdentitySessionNameUtilsTest {
    @Test
    fun `placeholder name trims whitespace`() {
        val name = placeholderName("  Focus  ")

        assertEquals(listOf("Focus"), name.givenNames)
        assertEquals("", name.sureName)
    }

    @Test
    fun `placeholder name falls back to identity when blank`() {
        val name = placeholderName("   ")

        assertEquals(listOf("Identity"), name.givenNames)
    }
}
