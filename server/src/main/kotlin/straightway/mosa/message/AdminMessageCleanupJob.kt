package straightway.mosa.message

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

class AdminMessageCleanupJob(
    private val adminMessageService: AdminMessageService,
    cleanupInterval: Duration,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            while (true) {
                delay(cleanupInterval)
                runCatching { adminMessageService.deleteExpired() }
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}
