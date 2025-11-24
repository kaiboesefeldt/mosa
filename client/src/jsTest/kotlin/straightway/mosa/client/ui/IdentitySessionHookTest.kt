package straightway.mosa.client.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import react.FC
import react.Props
import react.create
import react.dom.html.ReactHTML.div
import straightway.mosa.client.AppModule
import straightway.mosa.client.testing.cleanup
import straightway.mosa.client.testing.render

class IdentitySessionHookTest {
    private var originalFactory: IdentitySessionStateMachineFactory = identitySessionStateMachineFactory

    @BeforeTest
    fun setUp() {
        AppModule.start()
        originalFactory = identitySessionStateMachineFactory
    }

    @AfterTest
    fun tearDown() {
        identitySessionStateMachineFactory = originalFactory
        cleanup()
    }

    @Test
    fun `creates state machine only once`() = runTest {
        var creations = 0
        identitySessionStateMachineFactory =
            { config ->
                creations += 1
                originalFactory(config)
            }

        renderIdentitySessionHook()
        waitForHookToSettle()

        assertEquals(1, creations)
    }
}

private suspend fun waitForHookToSettle() {
    repeat(5) { delay(0) }
}

private fun renderIdentitySessionHook() {
    render(IdentitySessionHookHarness.create())
}

private val IdentitySessionHookHarness = FC<Props> {
    when (useIdentitySession()) {
        IdentityScreen.Loading -> Unit
        is IdentityScreen.Connected -> div { +"connected" }
        is IdentityScreen.Profile -> div { +"profile" }
    }
}
