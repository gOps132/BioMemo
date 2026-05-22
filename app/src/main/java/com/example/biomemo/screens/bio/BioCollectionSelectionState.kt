package com.example.biomemo.screens.bio

class BioCollectionSelectionState {
    private val selectedIds = linkedSetOf<String>()

    val count: Int
        get() = selectedIds.size

    val isEmpty: Boolean
        get() = selectedIds.isEmpty()

    fun contains(id: String): Boolean = id in selectedIds

    fun ids(): List<String> = selectedIds.toList()

    fun singleSelectedId(): String? = selectedIds.singleOrNull()

    fun toggle(id: String) {
        if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
    }

    fun selectAll(ids: Collection<String>) {
        selectedIds.clear()
        selectedIds += ids
    }

    fun clear() {
        selectedIds.clear()
    }

    fun retainVisibleIds(ids: Collection<String>) {
        selectedIds.retainAll(ids.toSet())
    }
}
