package com.mana.syncmart

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mana.syncmart.databinding.ListRecyclerLayoutBinding

class ListAdapter(
    private var list: List<ShoppingList>,
    private val onSelectionChanged: (Boolean, Int) -> Unit, // Now includes count for menu
    private val onListClicked: (ShoppingList) -> Unit
) : RecyclerView.Adapter<ListAdapter.ViewHolder>() {

    private val selectedItems = mutableSetOf<String>()
    private var isSelectionMode = false

    class ViewHolder(val binding: ListRecyclerLayoutBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListRecyclerLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val listItem = list[position]
        holder.binding.buttonName.text = listItem.listName.ifEmpty { "Unnamed List" }

        // Show or hide notification bubble
        holder.binding.notifBubble.apply {
            visibility = if (listItem.pendingItems.isNotEmpty()) View.VISIBLE else View.GONE
            text = listItem.pendingItems.size.toString()
        }

        // Get logged-in user's email
        val loggedInUserEmail = AuthUtils.getCurrentUser()?.email


        // Determine background based on ownership and selection status
        when {
            selectedItems.contains(listItem.id) -> holder.itemView.setBackgroundResource(R.drawable.selected_bg)
            listItem.owner == loggedInUserEmail -> holder.itemView.setBackgroundResource(R.drawable.list_bg_owner)
            else -> holder.itemView.setBackgroundResource(R.drawable.list_bg)
        }

        // Click to toggle selection if in selection mode
        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(listItem.id)
            } else {
                onListClicked(listItem)
            }
        }

        // Long press to enable selection mode
        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                isSelectionMode = true
                toggleSelection(listItem.id)
            }
            true
        }
    }


    override fun getItemCount(): Int = list.size

    @SuppressLint("NotifyDataSetChanged")
    private fun toggleSelection(listId: String) {
        if (selectedItems.contains(listId)) {
            selectedItems.remove(listId)
        } else {
            selectedItems.add(listId)
        }

        // If nothing is selected, exit selection mode
        isSelectionMode = selectedItems.isNotEmpty()

        // **Ensure UI updates**
        onSelectionChanged(isSelectionMode, selectedItems.size)
        notifyDataSetChanged()
    }



    fun getSelectedItems(): List<String> = selectedItems.toList()

    fun isSelectionModeActive(): Boolean {
        return isSelectionMode
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        onSelectionChanged(false, 0) // Reset toolbar UI
        notifyDataSetChanged()
    }


    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newLists: List<ShoppingList>) {
        list = newLists // Update the list reference
        notifyDataSetChanged() // Notify adapter of dataset change
    }


}