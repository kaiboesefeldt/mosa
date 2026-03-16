package straightway.mosa.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class IdentityAdminCapabilityLookupTest {
    @Test
    fun `resets to false without loading when gid is missing`() = runTest {
        val requestedGids = mutableListOf<String>()
        val lookup =
            IdentityAdminCapabilityLookup { gid ->
                requestedGids += gid
                true
            }
        val updates = mutableListOf<Boolean>()

        lookup.refresh(null) { updates += it }

        assertEquals(listOf(false), updates)
        assertEquals(emptyList(), requestedGids)
    }

    @Test
    fun `keeps only newest response when requests finish out of order`() = runTest {
        val first = CompletableDeferred<Boolean>()
        val second = CompletableDeferred<Boolean>()
        val lookup =
            IdentityAdminCapabilityLookup { gid ->
                when (gid) {
                    "gid-a" -> first.await()
                    "gid-b" -> second.await()
                    else -> error("unexpected gid: $gid")
                }
            }
        val updates = mutableListOf<Boolean>()

        launch { lookup.refresh("gid-a") { updates += it } }
        launch { lookup.refresh("gid-b") { updates += it } }
        testScheduler.advanceUntilIdle()
        second.complete(false)
        first.complete(true)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(false, false, false), updates)
    }

    @Test
    fun `applies newest admin state after superseding older request`() = runTest {
        val first = CompletableDeferred<Boolean>()
        val second = CompletableDeferred<Boolean>()
        val lookup =
            IdentityAdminCapabilityLookup { gid ->
                when (gid) {
                    "gid-a" -> first.await()
                    "gid-b" -> second.await()
                    else -> error("unexpected gid: $gid")
                }
            }
        val updates = mutableListOf<Boolean>()

        launch { lookup.refresh("gid-a") { updates += it } }
        launch { lookup.refresh("gid-b") { updates += it } }
        testScheduler.advanceUntilIdle()
        first.complete(true)
        second.complete(true)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(false, false, true), updates)
    }
}
