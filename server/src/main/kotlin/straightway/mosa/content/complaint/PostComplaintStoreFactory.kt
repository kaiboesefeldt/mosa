package straightway.mosa.content.complaint

import straightway.mosa.content.StructuredStateContext

fun interface PostComplaintStoreFactory {
    fun create(context: StructuredStateContext): PostComplaintStore
}
