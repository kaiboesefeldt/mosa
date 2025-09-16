package straightway.mosa.client

import io.ktor.client.HttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf

val KoinComponent.timeService get() = get<TimeService>()
val KoinComponent.misJwtService get() = get<MisJwtService>()
val KoinComponent.identityGenerator get() = get<IdentityGenerator>()
val KoinComponent.httpClient get() = get<HttpClient>()
fun KoinComponent.podAccess(podHost: String) = get<PodAccess> { parametersOf(podHost) }
val KoinComponent.subtleCrypto get() = get<SubtleCrypto>()
val KoinComponent.cryptoExport get() = get<CryptoExport>()
