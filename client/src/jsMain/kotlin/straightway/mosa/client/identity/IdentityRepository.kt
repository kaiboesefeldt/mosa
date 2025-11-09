package straightway.mosa.client.identity

interface IdentitySelectionRepository {
    suspend fun loadOrCreate(): IdentitySelection
    suspend fun movePrevious(): IdentitySelection?
    suspend fun moveNext(): IdentitySelection
    suspend fun deleteCurrent(): IdentitySelection
    suspend fun select(index: Int): IdentitySelection?
    suspend fun import(identity: Identity): IdentitySelection
}

class IdentityRepository(
    private val store: IdentityStore,
    private val generator: IdentityProvider,
) : IdentitySelectionRepository {
    override suspend fun loadOrCreate(): IdentitySelection {
        val stored = store.loadState()
        if (stored != null) return stored.toSelection()

        val generated = generator.generate()
        val initial = IdentityStoreState(listOf(generated), 0)
        store.storeState(initial)
        return initial.toSelection()
    }

    override suspend fun movePrevious(): IdentitySelection? {
        val current = store.loadState() ?: return null
        if (!current.hasPrevious()) return null
        val updated = current.withActiveIndex(current.activeIndex - 1)
        store.storeState(updated)
        return updated.toSelection()
    }

    override suspend fun moveNext(): IdentitySelection {
        val current = store.loadState()
        val updated =
            when {
                current == null -> {
                    val generated = generator.generate()
                    IdentityStoreState(listOf(generated), 0)
                }
                current.hasNext() -> current.withActiveIndex(current.activeIndex + 1)
                else -> {
                    val generated = generator.generate()
                    current.append(generated)
                }
            }
        store.storeState(updated)
        return updated.toSelection()
    }

    override suspend fun deleteCurrent(): IdentitySelection {
        val current = store.loadState()
        val updated =
            if (current == null || current.identities.size == 1) {
                val generated = generator.generate()
                IdentityStoreState(listOf(generated), 0)
            } else {
                val remaining = current.identities.toMutableList()
                remaining.removeAt(current.activeIndex)
                val nextIndex = current.activeIndex.coerceAtMost(remaining.lastIndex)
                IdentityStoreState(remaining.toList(), nextIndex)
            }
        store.storeState(updated)
        return updated.toSelection()
    }

    override suspend fun select(index: Int): IdentitySelection? {
        val current = store.loadState() ?: return null
        if (index !in current.identities.indices) return current.toSelection()
        if (index == current.activeIndex) return current.toSelection()
        val updated = current.withActiveIndex(index)
        store.storeState(updated)
        return updated.toSelection()
    }

    override suspend fun import(identity: Identity): IdentitySelection {
        val current = store.loadState()
        val updated =
            if (current == null) {
                IdentityStoreState(listOf(identity), 0)
            } else {
                val existingIndex = current.identities.indexOfFirst { it.gid == identity.gid }
                if (existingIndex >= 0) {
                    current.withActiveIndex(existingIndex)
                } else {
                    current.append(identity)
                }
            }
        store.storeState(updated)
        return updated.toSelection()
    }
}

data class IdentitySelection(
    val identity: Identity,
    val index: Int,
    val total: Int,
) {
    val hasPrevious: Boolean
        get() = index > 0

    val hasNext: Boolean
        get() = index + 1 < total
}

private fun IdentityStoreState.toSelection(): IdentitySelection =
    IdentitySelection(
        identity = current(),
        index = activeIndex,
        total = identities.size,
    )

private fun IdentityStoreState.hasPrevious(): Boolean = activeIndex > 0

private fun IdentityStoreState.hasNext(): Boolean = activeIndex < identities.lastIndex
