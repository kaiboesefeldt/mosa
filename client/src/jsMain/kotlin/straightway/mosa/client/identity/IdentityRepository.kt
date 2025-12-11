package straightway.mosa.client.identity

interface IdentitySelectionRepository {
    suspend fun loadOrCreate(): IdentitySelection
    suspend fun movePrevious(): IdentitySelection?
    suspend fun moveNext(): IdentitySelection
    suspend fun deleteCurrent(): IdentitySelection
    suspend fun select(index: Int): IdentitySelection?
    suspend fun import(identity: Identity): IdentitySelection
    suspend fun persist()
}

class IdentityRepository(
    private val store: IdentityStore,
    private val generator: IdentityProvider,
) : IdentitySelectionRepository {
    private var state: IdentityStoreState? = null
    private var persistedGids: Set<String> = emptySet()

    override suspend fun loadOrCreate(): IdentitySelection {
        val current = state ?: loadPersistedState()
        if (current != null) return selectionOf(current)
        val generated = generator.generate()
        val initial = IdentityStoreState(listOf(generated), 0)
        updateState(initial)
        return selectionOf(initial)
    }

    override suspend fun movePrevious(): IdentitySelection? {
        val current = loadStateOrNull() ?: return null
        if (!current.hasPrevious()) return null
        val updated = current.withActiveIndex(current.activeIndex - 1)
        updateState(updated)
        return selectionOf(updated)
    }

    override suspend fun moveNext(): IdentitySelection {
        val current = loadStateOrNull()
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
        updateState(updated)
        return selectionOf(updated)
    }

    override suspend fun deleteCurrent(): IdentitySelection {
        val current = loadStateOrNull()
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
        updateState(updated)
        return selectionOf(updated)
    }

    override suspend fun select(index: Int): IdentitySelection? {
        val current = loadStateOrNull() ?: return null
        if (index !in current.identities.indices) return selectionOf(current)
        if (index == current.activeIndex) return selectionOf(current)
        val updated = current.withActiveIndex(index)
        updateState(updated)
        return selectionOf(updated)
    }

    override suspend fun import(identity: Identity): IdentitySelection {
        val current = loadStateOrNull()
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
        updateState(updated)
        return selectionOf(updated)
    }

    override suspend fun persist() {
        val current = loadStateOrNull() ?: return
        val persistedIdentities =
            current.identities.filter { identity ->
                identity.gid == current.current().gid || persistedGids.contains(identity.gid)
            }
        if (persistedIdentities.isEmpty()) return
        val activeIndex = persistedIdentities.indexOfFirst { it.gid == current.current().gid }.coerceAtLeast(0)
        val storedState = IdentityStoreState(persistedIdentities, activeIndex)
        store.storeState(storedState)
        persistedGids = persistedGids + persistedIdentities.map { it.gid }
    }

    private suspend fun loadStateOrNull(): IdentityStoreState? {
        val cached = state
        if (cached != null) return cached
        return loadPersistedState()
    }

    private suspend fun loadPersistedState(): IdentityStoreState? {
        val stored = store.loadState() ?: return null
        rememberPersisted(stored)
        return stored
    }

    private fun rememberPersisted(newState: IdentityStoreState) {
        state = newState
        persistedGids = newState.identities.map { it.gid }.toSet()
    }

    private fun updateState(newState: IdentityStoreState) {
        val presentGids = newState.identities.map { it.gid }.toSet()
        persistedGids = persistedGids.intersect(presentGids)
        state = newState
    }

    private fun selectionOf(state: IdentityStoreState): IdentitySelection =
        IdentitySelection(
            identity = state.current(),
            index = state.activeIndex,
            total = state.identities.size,
            isPersisted = persistedGids.contains(state.current().gid),
        )
}

data class IdentitySelection(
    val identity: Identity,
    val index: Int,
    val total: Int,
    val isPersisted: Boolean,
) {
    val hasPrevious: Boolean
        get() = index > 0

    val hasNext: Boolean
        get() = index + 1 < total
}

private fun IdentityStoreState.hasPrevious(): Boolean = activeIndex > 0

private fun IdentityStoreState.hasNext(): Boolean = activeIndex < identities.lastIndex
