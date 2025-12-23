package straightway.mosa.defense

fun ewmaMovingAverage(
    previous: Double,
    sample: Double,
    alpha: Double,
): Double =
    when {
        !sample.isFinite() -> previous
        !previous.isFinite() -> sample
        else -> previous + alpha * (sample - previous)
    }
