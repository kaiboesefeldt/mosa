package straightway.mosa.client.content

import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.identity.Identity

typealias JwtBuilder = suspend (Identity, PodCertificate) -> String