package straightway.mosa.defense

class P2QuantileEstimator(
    private val quantile: Double,
) {
    init {
        require(quantile > 0.0 && quantile < 1.0) { "quantile must be in (0, 1), was $quantile" }
    }

    companion object {
        private const val MARKERS = 5
        private const val TARGET_QUANTILE_INDEX = 2
        private const val LEFT = 0
        private const val RIGHT = 4
    }

    private val warmup = DoubleArray(MARKERS)
    private var warmCount = 0

    private val markerHeights = DoubleArray(MARKERS)
    private val markerPositions = LongArray(MARKERS)
    private val desiredPositions = DoubleArray(MARKERS)
    private val desiredPositionIncrementsPerSample =
        doubleArrayOf(
            0.0,
            quantile / 2.0,
            quantile,
            (1.0 + quantile) / 2.0,
            1.0,
        )

    private var initialized = false
    private var sampleCount = 0L

    fun samples(): Long = sampleCount

    fun add(sample: Double) {
        if (!sample.isFinite()) return
        sampleCount++

        if (!initialized) {
            addWarmupSample(sample)
            return
        }

        val cell = placeSampleAndFindCell(sample)
        incrementMarkerPositionsAbove(cell)
        advanceDesiredPositions()
        adjustInteriorMarkersIfNeeded()
    }

    fun estimate(): Double {
        if (initialized) return markerHeights[TARGET_QUANTILE_INDEX]
        if (warmCount == 0) return Double.NaN
        return exactQuantileFromWarmup()
    }

    private fun addWarmupSample(sample: Double) {
        warmup[warmCount++] = sample
        if (warmCount == MARKERS) initFromWarmup()
    }

    private fun initFromWarmup() {
        val sorted = warmup.copyOf().apply { sort() }

        for (i in 0 until MARKERS) {
            markerHeights[i] = sorted[i]
            markerPositions[i] = i + 1L
        }

        desiredPositions[0] = 1.0
        desiredPositions[1] = 1.0 + 2.0 * quantile
        desiredPositions[2] = 1.0 + 4.0 * quantile
        desiredPositions[3] = 3.0 + 2.0 * quantile
        desiredPositions[4] = 5.0

        initialized = true
    }

    private fun exactQuantileFromWarmup(): Double {
        val sorted = warmup.copyOf(warmCount).apply { sort() }
        val idx = ((warmCount - 1) * quantile).toInt().coerceIn(0, warmCount - 1)
        return sorted[idx]
    }

    private fun placeSampleAndFindCell(sample: Double): Int =
        when {
            sample < markerHeights[LEFT] -> {
                markerHeights[LEFT] = sample
                0
            }
            sample < markerHeights[1] -> 0
            sample < markerHeights[2] -> 1
            sample < markerHeights[3] -> 2
            sample < markerHeights[RIGHT] -> 3
            else -> {
                markerHeights[RIGHT] = sample
                3
            }
        }

    private fun incrementMarkerPositionsAbove(cell: Int) {
        for (i in (cell + 1) until MARKERS) markerPositions[i]++
    }

    private fun advanceDesiredPositions() {
        for (i in 0 until MARKERS) desiredPositions[i] += desiredPositionIncrementsPerSample[i]
    }

    private fun adjustInteriorMarkersIfNeeded() {
        for (i in 1..3) {
            val delta = desiredPositions[i] - markerPositions[i].toDouble()

            val canMoveUp = delta >= 1.0 && (markerPositions[i + 1] - markerPositions[i] > 1)
            val canMoveDown = delta <= -1.0 && (markerPositions[i] - markerPositions[i - 1] > 1)

            if (!canMoveUp && !canMoveDown) continue

            val step = if (delta >= 0.0) 1 else -1
            val candidate = parabolicEstimate(i, step)

            markerHeights[i] =
                if (candidate > markerHeights[i - 1] && candidate < markerHeights[i + 1]) {
                    candidate
                } else {
                    linearEstimate(i, step)
                }

            markerPositions[i] += step
        }
    }

    private fun parabolicEstimate(
        i: Int,
        step: Int,
    ): Double {
        val n0 = markerPositions[i - 1].toDouble()
        val n1 = markerPositions[i].toDouble()
        val n2 = markerPositions[i + 1].toDouble()

        val x0 = markerHeights[i - 1]
        val x1 = markerHeights[i]
        val x2 = markerHeights[i + 1]

        val d = step.toDouble()

        val a = d / (n2 - n0)
        val term1 = (n1 - n0 + d) * (x2 - x1) / (n2 - n1)
        val term2 = (n2 - n1 - d) * (x1 - x0) / (n1 - n0)

        return x1 + a * (term1 + term2)
    }

    private fun linearEstimate(
        i: Int,
        step: Int,
    ): Double {
        val j = i + step
        val ni = markerPositions[i].toDouble()
        val nj = markerPositions[j].toDouble()
        return markerHeights[i] + step.toDouble() * (markerHeights[j] - markerHeights[i]) / (nj - ni)
    }
}
