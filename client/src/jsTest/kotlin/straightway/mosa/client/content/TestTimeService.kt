package straightway.mosa.client.content

import straightway.mosa.client.aux.TimeService

class TestTimeService(
    var nowValue: Long,
) : TimeService {
    override val now: Long
        get() = nowValue
}
