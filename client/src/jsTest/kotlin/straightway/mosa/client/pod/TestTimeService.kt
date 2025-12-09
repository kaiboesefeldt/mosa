package straightway.mosa.client.pod

import straightway.mosa.client.aux.TimeService

class TestTimeService(
    var nowValue: Long,
) : TimeService {
    override val now: Long
        get() = nowValue
}
