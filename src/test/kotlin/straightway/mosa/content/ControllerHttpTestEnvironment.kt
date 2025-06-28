package straightway.mosa.content

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import straightway.mosa.base.BaseController
import straightway.mosa.base.register
import straightway.mosa.installNetworkServerPlugins

class ControllerHttpTestEnvironment(
    val controllers: List<BaseController>,
) {
    constructor(controller: BaseController) : this(listOf(controller))

    operator fun invoke(
        vararg controllers: BaseController,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) {
        testApplication {
            application {
                installNetworkServerPlugins()
                routing {
                    (this@ControllerHttpTestEnvironment.controllers + controllers).forEach {
                        register(it)
                    }
                }
            }
            client =
                createClient {
                    install(ContentNegotiation) {
                        jackson()
                    }
                }
            block()
        }
    }
}
