package com.mana.syncmart

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.mana.syncmart.databinding.ListElementRecyclerLayoutBinding

class ItemAdapter(
    private var listId: String,
    private var items: MutableList<Pair<String, Boolean>>,
    private val onItemChecked: (String) -> Unit,
    private val showButtons: Boolean,
    private val selectionListener: SelectionListener? = null
) : RecyclerView.Adapter<ItemAdapter.ViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val selectedItems = mutableSetOf<String>()
    private var selectionMode = false

    interface SelectionListener {
        fun onSelectionStarted()
        fun onSelectionChanged(selectedCount: Int)
        fun onSelectionCleared()
    }

    class ViewHolder(binding: ListElementRecyclerLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        val itemName: TextView = binding.buttonName
        val btnImportant: ImageButton = binding.btnImportant
        val btnSettings: ImageButton = binding.btnSettings
        val itemRoot = binding.root
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListElementRecyclerLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (item, isImportant) = items[position]
        val context = holder.itemView.context

        holder.itemName.text = item

        val visibility = if (showButtons && !selectionMode) View.VISIBLE else View.INVISIBLE
        holder.btnImportant.visibility = visibility
        holder.btnSettings.visibility = visibility

        val isSelected = selectedItems.contains(item)

        // Set background based on selection or importance
        if (isSelected) {
            holder.itemRoot.setBackgroundResource(R.drawable.selected_bg)
        } else {
            val backgroundRes = if (isImportant) {
                R.drawable.list_element_recycler_bg_important
            } else {
                R.drawable.list_element_recycler_bg
            }
            holder.itemRoot.setBackgroundResource(backgroundRes)
        }

        holder.itemName.setTextColor(
            context.getColor(if (isImportant || isSelected) R.color.white else R.color.black)
        )

        // Long press: enter selection mode
        holder.itemRoot.setOnLongClickListener {
            if (!selectionMode) {
                selectionMode = true
                selectedItems.add(item)
                selectionListener?.onSelectionStarted()
                notifyItemChanged(position)
                selectionListener?.onSelectionChanged(selectedItems.size)
            }
            true
        }

        // Click: toggle selection if in selection mode, otherwise normal action
        holder.itemRoot.setOnClickListener {
            if (selectionMode) {
                toggleSelection(item, position)
            }
        }

        // Set correct icon based on importance
        holder.btnImportant.setImageResource(
            if (isImportant) R.drawable.ic_star_important else R.drawable.ic_star
        )

        // Star icon click
        holder.btnImportant.setOnClickListener {
            if (!selectionMode) {
                val newImportant = !isImportant
                items[position] = item to newImportant
                notifyItemChanged(position)
                db.collection("shopping_lists")
                    .document(listId)
                    .update("pendingItems.$item", newImportant)
            }
        }

        holder.btnSettings.setOnClickListener {
            if (!selectionMode) onItemChecked(item)
        }
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newItems: Map<String, Boolean>) {
        items.clear()
        items.addAll(newItems.map { it.key to it.value })
        selectedItems.clear()
        selectionMode = false
        notifyDataSetChanged()
        selectionListener?.onSelectionCleared()
    }

    private fun toggleSelection(item: String, position: Int) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
        } else {
            selectedItems.add(item)
        }

        notifyItemChanged(position)

        if (selectedItems.isEmpty()) {
            selectionMode = false
            selectionListener?.onSelectionCleared()
        } else {
            selectionListener?.onSelectionChanged(selectedItems.size)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearSelection() {
        selectionMode = false
        selectedItems.clear()
        notifyDataSetChanged()
        selectionListener?.onSelectionCleared()
    }

    fun getSelectedItems(): List<String> = selectedItems.toList()
}
