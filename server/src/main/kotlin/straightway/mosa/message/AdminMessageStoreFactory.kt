package straightway.mosa.message

import straightway.mosa.structuredstate.StructuredStateContext

fun interface AdminMessageStoreFactory {
    fun create(context: StructuredStateContext): AdminMessageStore
}
