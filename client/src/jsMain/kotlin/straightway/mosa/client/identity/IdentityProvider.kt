package straightway.mosa.client.identity

interface IdentityProvider {
    suspend fun generate(): Identity
}
