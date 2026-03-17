package straightway.mosa.message

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class MutableClock(
    initialInstant: Instant,
    private val zoneId: ZoneId = ZoneId.of("UTC"),
) : Clock() {
    private var currentInstant: Instant = initialInstant

    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)

    override fun instant(): Instant = currentInstant

    fun advanceSeconds(seconds: Long) {
        currentInstant = currentInstant.plusSeconds(seconds)
    }
}
