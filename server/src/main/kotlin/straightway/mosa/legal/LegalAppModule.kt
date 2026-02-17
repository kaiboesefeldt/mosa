package straightway.mosa.legal

import org.koin.core.module.Module
import org.koin.core.qualifier.StringQualifier
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import straightway.mosa.base.BaseController
import java.nio.file.Path

fun Module.configureLegalAppModule() {
    single(legalPrivacyProfilePathQualifier) { Path.of("mosa_privacy.yaml") }
    single {
        LegalService(
            operatorProfilePath = get(legalPrivacyProfilePathQualifier),
        )
    }
    single { LegalController(get()) } bind BaseController::class
}

private val legalPrivacyProfilePathQualifier: StringQualifier = named("legalPrivacyProfilePath")
