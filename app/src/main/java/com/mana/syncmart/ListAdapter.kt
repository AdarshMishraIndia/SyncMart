package com.mana.syncmart

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mana.syncmart.databinding.ListRecyclerLayoutBinding

class ListAdapter(
    private val onSelectionChanged: (Boolean, Int) -> Unit,
    private val onListClicked: (ShoppingList) -> Unit
) : ListAdapter<ShoppingList, ListAdapter.ViewHolder>(ShoppingListItemCallback()) {

    private val selectedItems = mutableSetOf<String>()
    private var isSelectionMode = false

    inner class ViewHolder(private val binding: ListRecyclerLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ShoppingList) {
            // Explicitly set the name to avoid glitches during recycling
            binding.buttonName.text = item.listName.ifEmpty { "Unnamed List" }
            updateNotifBubble(item)
            updateItemBackground(item)

            binding.root.setOnClickListener {
                if (isSelectionMode) toggleSelection(item.id) else onListClicked(item)
            }

            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    isSelectionMode = true
                    toggleSelection(item.id)
                }
                true
            }
        }

        private fun updateNotifBubble(item: ShoppingList) {
            binding.notifBubble.apply {
                visibility = if (item.pendingItems.isNotEmpty()) View.VISIBLE else View.GONE
                text = item.pendingItems.size.toString()
            }
        }

        private fun updateItemBackground(item: ShoppingList) {
            val userEmail = FirebaseAuth.getInstance().currentUser?.email
            val backgroundRes = when {
                selectedItems.contains(item.id) -> R.drawable.selected_bg
                item.owner == userEmail -> R.drawable.list_bg_owner
                else -> R.drawable.list_bg
            }

            binding.root.setBackgroundResource(backgroundRes)
        }


    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListRecyclerLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private fun toggleSelection(listId: String) {
        val wasSelected = selectedItems.remove(listId)
        if (!wasSelected) selectedItems.add(listId)

        isSelectionMode = selectedItems.isNotEmpty()
        onSelectionChanged(isSelectionMode, selectedItems.size)

        notifyItemChanged(currentList.indexOfFirst { it.id == listId })
    }

    fun getSelectedItems(): List<String> = selectedItems.toList()

    fun isSelectionModeActive(): Boolean = isSelectionMode

    fun clearSelection() {
        val previousSelection = selectedItems.toSet()
        selectedItems.clear()
        isSelectionMode = false
        onSelectionChanged(false, 0)

        // Refresh only previously selected items
        previousSelection.forEach { id ->
            val index = currentList.indexOfFirst { it.id == id }
            if (index >= 0) notifyItemChanged(index)
        }
    }

    fun updateListPreserveSelection(newLists: List<ShoppingList>) {
        val sortedLists = newLists.sortedBy { it.position }  // Ensure the lists are sorted by their position.

        // Save the current selection so it can be restored after the list update
        val previousSelection = selectedItems.toSet()

        // Use submitList to trigger a diff update (this is more efficient than notifyDataSetChanged())
        submitList(sortedLists) {
            // After the list is submitted, restore the selection state
            selectedItems.clear()

            // Add the items that were previously selected (if they still exist in the updated list)
            selectedItems.addAll(
                sortedLists.filter { previousSelection.contains(it.id) }.map { it.id }
            )

            // Set selection mode based on whether there are any selected items
            isSelectionMode = selectedItems.isNotEmpty()

            // Update the selection status externally
            onSelectionChanged(isSelectionMode, selectedItems.size)
        }
    }


    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return

        val updatedList = currentList.toMutableList()
        val item = updatedList.removeAt(fromPosition)
        updatedList.add(toPosition, item)

        submitList(updatedList) {
            persistItemPositions(updatedList)
        }
    }

    private fun persistItemPositions(lists: List<ShoppingList>) {
        val batch = FirebaseFirestore.getInstance().batch()

        lists.forEachIndexed { index, item ->
            item.position = index
            val docRef = FirebaseFirestore.getInstance().collection("shopping_lists").document(item.id)
            batch.update(docRef, "position", index)
        }

        // Commit the batch in one go
        batch.commit()
            .addOnSuccessListener {
                // Handle success
            }
            .addOnFailureListener {
                // Handle failure
            }
    }

}

