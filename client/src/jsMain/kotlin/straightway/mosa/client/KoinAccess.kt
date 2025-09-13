package straightway.mosa.client

import org.koin.core.component.KoinComponent
import org.koin.core.parameter.parametersOf

fun KoinComponent.pod(podHost: String) = getKoin().get<PodAccess> { parametersOf(podHost) }

val KoinComponent.identityGenerator get() = getKoin().get<IdentityGenerator>()