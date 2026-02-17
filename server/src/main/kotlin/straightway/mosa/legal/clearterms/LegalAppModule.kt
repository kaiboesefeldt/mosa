package straightway.mosa.legal.clearterms

import org.koin.core.module.Module
import org.koin.dsl.bind
import straightway.mosa.base.BaseController

fun Module.configureLegalAppModule() {
    single { LegalService() }
    single { LegalController(get()) } bind BaseController::class
}
