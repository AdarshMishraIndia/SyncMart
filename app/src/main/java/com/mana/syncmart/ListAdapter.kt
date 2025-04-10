package com.mana.syncmart

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mana.syncmart.databinding.ListRecyclerLayoutBinding

class ListAdapter(
    private val onSelectionChanged: (Boolean, Int) -> Unit, // Now includes count for menu
    private val onListClicked: (ShoppingList) -> Unit
) : ListAdapter<ShoppingList, ListAdapter.ViewHolder>(ShoppingListItemCallback()) {

    private val selectedItems = mutableSetOf<String>()
    private var isSelectionMode = false

    class ViewHolder(val binding: ListRecyclerLayoutBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListRecyclerLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val listItem = getItem(position)
        holder.binding.buttonName.text = listItem.listName.ifEmpty { "Unnamed List" }

        // Notification bubble visibility
        holder.binding.notifBubble.apply {
            visibility = if (listItem.pendingItems.isNotEmpty()) View.VISIBLE else View.GONE
            text = listItem.pendingItems.size.toString()
        }

        val loggedInUserEmail = AuthUtils.getCurrentUser()?.email

        // Visual background state
        when {
            selectedItems.contains(listItem.id) -> holder.itemView.setBackgroundResource(R.drawable.selected_bg)
            listItem.owner == loggedInUserEmail -> holder.itemView.setBackgroundResource(R.drawable.list_bg_owner)
            else -> holder.itemView.setBackgroundResource(R.drawable.list_bg)
        }

        // Handle click events
        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(listItem.id)
            } else {
                onListClicked(listItem)
            }
        }

        // Handle long-press to enable selection
        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                isSelectionMode = true
                toggleSelection(listItem.id)
            }
            true
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun toggleSelection(listId: String) {
        if (selectedItems.contains(listId)) {
            selectedItems.remove(listId)
        } else {
            selectedItems.add(listId)
        }

        isSelectionMode = selectedItems.isNotEmpty()
        onSelectionChanged(isSelectionMode, selectedItems.size)
        notifyDataSetChanged() // Needed to update backgrounds instantly
    }

    fun getSelectedItems(): List<String> = selectedItems.toList()

    fun isSelectionModeActive(): Boolean = isSelectionMode

    @SuppressLint("NotifyDataSetChanged")
    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        onSelectionChanged(false, 0)
        notifyDataSetChanged()
    }
    fun updateListPreserveSelection(newLists: List<ShoppingList>) {
        val previousSelection = selectedItems.toSet()

        submitList(newLists)

        selectedItems.clear()
        selectedItems.addAll(
            newLists.filter { previousSelection.contains(it.id) }.map { it.id }
        )

        isSelectionMode = selectedItems.isNotEmpty()
        onSelectionChanged(isSelectionMode, selectedItems.size)
    }
}