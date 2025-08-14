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
    var items: MutableList<Pair<String, ShoppingItem>>,
    private val onItemChecked: (String) -> Unit,
    private val showButtons: Boolean, // true = pending mode, false = finished mode
    private val selectionListener: SelectionListener? = null,
    private val onInfoClick: ((ShoppingItem, String) -> Unit)? = null // NEW: Info click callback
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
        val (itemName, item) = items[position]
        val context = holder.itemView.context

        holder.itemName.text = itemName

        if (showButtons) {
            // Pending items mode
            holder.btnImportant.visibility = View.VISIBLE
            holder.btnSettings.visibility = View.VISIBLE
            holder.btnSettings.setImageResource(R.drawable.ic_tick)

            holder.btnImportant.setImageResource(
                if (item.important) R.drawable.ic_star_important else R.drawable.ic_star
            )

            holder.btnImportant.setOnClickListener {
                if (!selectionMode) {
                    val newImportant = !item.important
                    items[position] = itemName to item.copy(important = newImportant)
                    notifyItemChanged(position)

                    db.collection("shopping_lists")
                        .document(listId)
                        .update("items.$itemName.important", newImportant)
                }
            }

            holder.btnSettings.setOnClickListener {
                if (!selectionMode) onItemChecked(itemName)
            }

            // Enable selection in pending mode
            holder.itemRoot.setOnLongClickListener {
                if (!selectionMode) {
                    selectionMode = true
                    selectedItems.add(itemName)
                    selectionListener?.onSelectionStarted()
                    notifyItemChanged(position)
                    selectionListener?.onSelectionChanged(selectedItems.size)
                }
                true
            }

            holder.itemRoot.setOnClickListener {
                if (selectionMode) toggleSelection(itemName, position)
            }

        } else {
            // Finished items mode
            holder.btnImportant.visibility = View.INVISIBLE
            holder.btnSettings.visibility = View.VISIBLE
            holder.btnSettings.imageTintList = null
            holder.btnSettings.setImageResource(R.drawable.ic_info)
            holder.btnSettings.setOnClickListener {
                onInfoClick?.invoke(item, itemName) // Call fragment's handler
            }

            // No selection in finished mode
            holder.itemRoot.setOnClickListener(null)
            holder.itemRoot.setOnLongClickListener(null)
        }

        val isSelected = selectedItems.contains(itemName)
        holder.itemRoot.setBackgroundResource(
            when {
                isSelected -> R.drawable.selected_bg
                item.important -> R.drawable.list_element_recycler_bg_important
                else -> R.drawable.list_element_recycler_bg
            }
        )

        holder.itemName.setTextColor(
            context.getColor(if (item.important || isSelected) R.color.white else R.color.black)
        )
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newItems: Map<String, ShoppingItem>) {
        items.clear()
        if (showButtons) {
            // pending items
            items.addAll(
                newItems.entries
                    .filter { it.value.pending }
                    .sortedByDescending { it.value.addedAt ?: com.google.firebase.Timestamp.now() }
                    .map { it.key to it.value }
            )
        } else {
            // finished items
            items.addAll(
                newItems.entries
                    .filter { !it.value.pending }
                    .sortedByDescending { it.value.addedAt ?: com.google.firebase.Timestamp.now() }
                    .map { it.key to it.value }
            )
        }
        selectedItems.clear()
        selectionMode = false
        notifyDataSetChanged()
        selectionListener?.onSelectionCleared()
    }

    fun removeItem(itemName: String) {
        val index = items.indexOfFirst { it.first == itemName }
        if (index != -1) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    private fun toggleSelection(item: String, position: Int) {
        if (!showButtons) return // no selection in finished mode
        if (selectedItems.contains(item)) selectedItems.remove(item) else selectedItems.add(item)
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
        if (!showButtons) return
        selectionMode = false
        selectedItems.clear()
        notifyDataSetChanged()
        selectionListener?.onSelectionCleared()
    }

    fun getSelectedItems(): List<String> = selectedItems.toList()
}
