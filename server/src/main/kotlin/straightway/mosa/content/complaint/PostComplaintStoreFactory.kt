package straightway.mosa.content.complaint

import straightway.mosa.structuredstate.StructuredStateContext

fun interface PostComplaintStoreFactory {
    fun create(context: StructuredStateContext): PostComplaintStore
}
