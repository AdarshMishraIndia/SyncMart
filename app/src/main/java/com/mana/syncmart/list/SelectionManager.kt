package com.mana.syncmart.list

import com.mana.syncmart.dataclass.ShoppingItem

class SelectionManager {
    private val selectedItems = mutableSetOf<String>()

    fun isSelectionActive() = selectedItems.isNotEmpty()
    fun getSelectedItems() = selectedItems.toSet()

    fun toggleSelection(itemName: String) {
        if (selectedItems.contains(itemName)) selectedItems.remove(itemName)
        else selectedItems.add(itemName)
    }

    fun isSelected(itemName: String) = selectedItems.contains(itemName)

    fun clearSelection() {
        selectedItems.clear()
    }

    /**
     * Returns positions of selected items based on the adapter's items list
     */
    fun getSelectedPositions(adapterItems: List<Pair<String, ShoppingItem>>): List<Int> {
        return adapterItems.mapIndexedNotNull { index, (name, _) ->
            if (selectedItems.contains(name)) index else null
        }
    }
}