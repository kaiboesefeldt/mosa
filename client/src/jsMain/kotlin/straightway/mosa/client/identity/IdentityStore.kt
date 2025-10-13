package straightway.mosa.client.identity

interface IdentityStore {
    suspend fun load(): Identity?
    suspend fun store(identity: Identity)
}
