package straightway.mosa.identity.moderation

import org.koin.core.module.Module
import org.koin.dsl.bind
import straightway.mosa.HasherFactory
import straightway.mosa.base.BaseController
import straightway.mosa.defense.adminAllowList

fun Module.configureIdentityModerationModule() {
    single {
        IdentityModerationService(
            adminAllowList = get(adminAllowList),
            blockedIdentityStore = get(),
            identityDeleter = get(),
            expectedGidByteLength = get<HasherFactory>()().digest(byteArrayOf()).size,
        )
    }
    single { IdentityModerationController(get()) } bind BaseController::class
}
