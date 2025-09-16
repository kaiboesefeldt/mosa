package straightway.mosa.client.aux

import kotlin.js.Date

interface TimeService {
    val now: Long
    companion object {
        operator fun invoke(): TimeService = object : TimeService {
            override val now get() = Date.now().toLong() / 1000L
        }
    }
}