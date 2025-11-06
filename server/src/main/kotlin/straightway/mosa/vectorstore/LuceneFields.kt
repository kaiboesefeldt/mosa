package straightway.mosa.vectorstore

import org.apache.lucene.search.Sort
import org.apache.lucene.search.SortField

internal const val ID_FIELD_NAME = "id"
internal const val TEXT_FIELD_NAME = "text"
internal const val VECTOR_FIELD_NAME = "vector"
internal const val TIME_ADDED_FIELD_NAME = "timeadded"

internal val SPECIAL_FIELDS =
    setOf(
        ID_FIELD_NAME,
        TEXT_FIELD_NAME,
        VECTOR_FIELD_NAME,
        TIME_ADDED_FIELD_NAME,
    )

internal val SORT_BY_TIME_ADDED = Sort(SortField(TIME_ADDED_FIELD_NAME, SortField.Type.LONG, true))
