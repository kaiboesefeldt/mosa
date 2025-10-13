package straightway.mosa.client.identity

class IdentityRepository(
    private val store: IdentityStore,
    private val generator: IdentityProvider,
) {
    suspend fun loadOrCreate(): Identity {
        val stored = store.load()
        if (stored != null) return stored

        val generated = generator.generate()
        store.store(generated)
        return generated
    }

    suspend fun regenerate(): Identity {
        val generated = generator.generate()
        store.store(generated)
        return generated
    }
}
