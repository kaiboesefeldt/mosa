package straightway.mosa.content.snapshot

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FreezeSnapshotArchiveKeyTest {
    @Test
    fun `equals compares byte array contents`() {
        val first = FreezeSnapshotArchiveKey("archive-2026", byteArrayOf(1, 2, 3))
        val second = FreezeSnapshotArchiveKey("archive-2026", byteArrayOf(1, 2, 3))

        assertEquals(first, second)
    }

    @Test
    fun `hash code matches when byte array contents match`() {
        val first = FreezeSnapshotArchiveKey("archive-2026", byteArrayOf(1, 2, 3))
        val second = FreezeSnapshotArchiveKey("archive-2026", byteArrayOf(1, 2, 3))

        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `equals detects different byte array contents`() {
        val first = FreezeSnapshotArchiveKey("archive-2026", byteArrayOf(1, 2, 3))
        val second = FreezeSnapshotArchiveKey("archive-2026", byteArrayOf(1, 2, 4))

        assertNotEquals(first, second)
    }
}
