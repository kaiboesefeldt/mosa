package straightway.mosa.defense.requestguard

import kotlin.math.max

private const val MAX_POST_LENGTH = 1800L
private const val MAX_ITEM_ID_LENGTH = 64L
private const val JSON_OVERHEAD_BYTES = 256L
private const val TEXT_BYTES_PER_CHAR = 4L
private const val SAFETY_MARGIN_BYTES = 1024L

fun defaultMaxRequestBodyBytes(maxFetchItems: Int): Long {
    val publishBytes = (MAX_POST_LENGTH * TEXT_BYTES_PER_CHAR) + JSON_OVERHEAD_BYTES
    val fetchBytes = (maxFetchItems.toLong() * (MAX_ITEM_ID_LENGTH + 3L)) + JSON_OVERHEAD_BYTES
    return max(publishBytes, fetchBytes) + SAFETY_MARGIN_BYTES
}
