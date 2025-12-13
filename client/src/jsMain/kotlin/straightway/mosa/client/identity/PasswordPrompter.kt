package straightway.mosa.client.identity

import kotlin.js.js
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.browser.window

interface PasswordPrompter {
    suspend fun promptNewPassword(): String?
    suspend fun promptExistingPassword(): String?
}

internal enum class PasswordPromptKind {
    Create,
    Enter,
}

internal data class PasswordPromptState(
    val kind: PasswordPromptKind,
    val result: CompletableDeferred<String?> = CompletableDeferred(),
)

internal object PasswordPromptController {
    private val _state = MutableStateFlow<PasswordPromptState?>(null)
    val state: StateFlow<PasswordPromptState?> = _state

    suspend fun prompt(kind: PasswordPromptKind): String? {
        val state = PasswordPromptState(kind)
        _state.value = state
        return try {
            state.result.await()
        } finally {
            clear(state)
        }
    }

    fun confirm(state: PasswordPromptState, password: String) {
        resolve(state, password)
    }

    fun cancel(state: PasswordPromptState) {
        resolve(state, null)
    }

    private fun resolve(state: PasswordPromptState, value: String?) {
        if (_state.value !== state || state.result.isCompleted) return
        state.result.complete(value)
        clear(state)
    }

    private fun clear(state: PasswordPromptState) {
        _state.update { current ->
            if (current === state) null else current
        }
    }
}

class BrowserPasswordPrompter : PasswordPrompter {
    override suspend fun promptNewPassword(): String? {
        bypassPassword()?.let { return it }
        return PasswordPromptController.prompt(PasswordPromptKind.Create)
    }

    override suspend fun promptExistingPassword(): String? {
        bypassPassword()?.let { return it }
        return PasswordPromptController.prompt(PasswordPromptKind.Enter)
    }

    private fun bypassPassword(): String? {
        val hasKarma = js("typeof __karma__ !== 'undefined'") as Boolean
        if (hasKarma) return "test-password"
        val override = window.asDynamic().__MOSA_TEST_PASSWORD
        if (override != undefined) return override as? String
        return null
    }
}
