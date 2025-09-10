package straightway.mosa

import com.auth0.jwt.interfaces.DecodedJWT

interface TokenUtil {
    fun decode(jwt: String): DecodedJWT

    fun isValidMisJwt(jwt: DecodedJWT): Boolean
}
