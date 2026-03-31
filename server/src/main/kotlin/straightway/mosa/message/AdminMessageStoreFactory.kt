package straightway.mosa.message

import straightway.mosa.content.StructuredStateContext

fun interface AdminMessageStoreFactory {
    fun create(context: StructuredStateContext): AdminMessageStore
}
