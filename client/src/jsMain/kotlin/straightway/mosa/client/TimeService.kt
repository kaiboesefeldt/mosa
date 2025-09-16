package straightway.mosa.client

interface TimeService {
    val now: Long
    companion object {
        operator fun invoke(): TimeService = object : TimeService {
            override val now get() = kotlin.js.Date.now().toLong() / 1000L
        }
    }
}