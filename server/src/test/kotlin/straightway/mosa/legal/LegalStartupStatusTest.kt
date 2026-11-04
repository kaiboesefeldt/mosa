package straightway.mosa.legal

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegalStartupStatusTest {
    @Test
    fun `checks both legal sources even when the first fails`() {
        var imprintChecked = false
        val status =
            LegalStartupStatus(
                checkProfiles = { error("invalid profiles") },
                checkImprint = {
                    imprintChecked = true
                    error("invalid imprint")
                },
            )

        assertTrue(imprintChecked)
        assertFalse(status.complete)
    }

    @Test
    fun `reports complete when both sources validate`() {
        assertTrue(LegalStartupStatus(checkProfiles = {}, checkImprint = {}).complete)
    }
}
