package straightway.mosa.client.identity

interface IdentityStore {
    suspend fun loadState(): IdentityStoreState?
    suspend fun storeState(state: IdentityStoreState)
    suspend fun clearState()
}

data class IdentityStoreState(
    val identities: List<Identity>,
    val activeIndex: Int,
) {
    init {
        require(identities.isNotEmpty()) { "Identities must not be empty" }
        require(activeIndex in identities.indices) { "Active index out of bounds" }
    }

    fun current(): Identity = identities[activeIndex]

    fun withActiveIndex(index: Int): IdentityStoreState =
        copy(activeIndex = index)

    fun append(identity: Identity): IdentityStoreState =
        copy(
            identities = identities + identity,
            activeIndex = identities.size,
        )
}
