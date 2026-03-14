package straightway.mosa.client.legal

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.koin.core.context.GlobalContext
import org.koin.core.component.get
import org.koin.dsl.module
import react.create
import straightway.mosa.client.AppModule
import straightway.mosa.client.IdentityApp
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.testing.cleanup
import straightway.mosa.client.testing.render
import straightway.mosa.client.testing.screen
import straightway.mosa.client.testing.userEvent

@OptIn(ExperimentalWasmJsInterop::class)
class LegalImprintIntegrationTest {
    private lateinit var overrideModule: org.koin.core.module.Module

    @BeforeTest
    fun setUp() {
        AppModule.start()
        overrideModule =
            module {
                single {
                    LegalImprintService(
                        podBaseProvider = fixedImprintPodBaseProvider("https://pod.example"),
                        podAccessFactory = fixedImprintPodAccessFactory(),
                        podBaseResolver = { "https://pod.example" },
                    )
                }
            }
        GlobalContext.get().loadModules(listOf(overrideModule), allowOverride = true)
    }

    @AfterTest
    fun tearDown() {
        cleanup()
        if (this::overrideModule.isInitialized) {
            GlobalContext.get().unloadModules(listOf(overrideModule))
        }
    }

    @Test
    fun `opens imprint from legal menu and renders loaded content`() = runTest {
        val result = render(IdentityApp.create())
        val user = userEvent.setup()
        val translator = AppModule.get<Translator>()

        user.click(screen.getByRole("button", json("name" to translator.translate("legal.button.label")))).await()
        user.click(screen.getByRole("link", json("name" to translator.translate("legal.imprint.label")))).await()

        eventually {
            val text = result.container.textContent.orEmpty()
            check(text.contains("Example Pod GmbH"))
            check(text.contains("legal@example.invalid"))
        }
    }

    private suspend fun eventually(assertion: () -> Unit) {
        repeat(40) {
            runCatching {
                assertion()
                return
            }
            delay(25)
        }
        error("Condition was not met")
    }
}
