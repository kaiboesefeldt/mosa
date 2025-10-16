@file:JsModule("@testing-library/user-event")
@file:JsNonModule

package straightway.mosa.client.testing

import kotlin.js.Promise

external val userEvent: UserEventModule

external interface UserEventModule {
    fun setup(): UserEventApi
}

external interface UserEventApi {
    fun click(element: dynamic): Promise<Unit>
}
