package straightway.mosa.defense

interface Estimator {
    val name: String
    val samples: Long

    fun add(sample: Double)

    fun estimate(): Double
}
