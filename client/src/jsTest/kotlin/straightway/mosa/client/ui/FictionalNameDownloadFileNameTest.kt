package straightway.mosa.client.ui

import straightway.mosa.identity.FictionalName
import kotlin.test.Test
import kotlin.test.assertEquals

class FictionalNameDownloadFileNameTest {

    @Test
    fun `builds camel case from identity parts`() {
        val name = FictionalName(
            givenNames = listOf("Ada", "Lovelace"),
            sureName = "de Byron",
            addendum = "III"
        )

        assertEquals("AdaLovelaceIIIDeByron", name.toIdentityDownloadFileName())
    }

    @Test
    fun `returns fallback when parts become empty`() {
        val name = FictionalName(
            givenNames = listOf("???"),
            sureName = "!!!",
            addendum = "***"
        )

        assertEquals("IdentityKeys", name.toIdentityDownloadFileName())
    }
}
