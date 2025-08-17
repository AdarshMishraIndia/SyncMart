package com.mana.syncmart

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mana.syncmart.databinding.ListElementRecyclerLayoutBinding
import android.view.Gravity

class ItemAdapter(
    var items: MutableList<Pair<String, ShoppingItem>>,
    private val selectionManager: SelectionManager? = null,
    private val onItemClick: ((String) -> Unit)? = null,
    private val onInfoClick: ((ShoppingItem, String) -> Unit)? = null,
    private val onStarClick: ((Int) -> Unit)? = null,
    private val onTickClick: ((String) -> Unit)? = null,
    private val isFinishedMode: Boolean = false,
    private val onSelectionChanged: ((selectedCount: Int) -> Unit)? = null,
    private val onSelectionCleared: (() -> Unit)? = null
) : RecyclerView.Adapter<ItemAdapter.ViewHolder>() {

    class ViewHolder(binding: ListElementRecyclerLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        val itemName: TextView = binding.itemName
        val btnStar: ImageButton = binding.btnStar
        val btnTick: ImageButton = binding.btnTick
        val btnInfo: ImageButton = binding.btnInfo
        val itemRoot = binding.root
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListElementRecyclerLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (itemName, item) = items[position]
        holder.itemName.text = itemName

        // Star button
        holder.btnStar.setImageResource(if (item.important) R.drawable.ic_star_important else R.drawable.ic_star)
        holder.btnStar.setOnClickListener { onStarClick?.invoke(position) }

        // Tick button
        holder.btnTick.setImageResource(R.drawable.ic_tick)
        holder.btnTick.setOnClickListener { onTickClick?.invoke(itemName) }

        // Info button
        holder.btnInfo.setOnClickListener { onInfoClick?.invoke(item, itemName) }

        // Selection & finished mode visibility
        val isSelected = selectionManager?.isSelected(itemName) == true
        if (isFinishedMode) {
            holder.btnStar.visibility = View.GONE
            holder.btnTick.visibility = View.GONE
            (holder.btnInfo.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.END
        } else {
            holder.btnStar.visibility = if (isSelected) View.INVISIBLE else View.VISIBLE
            holder.btnTick.visibility = if (isSelected) View.INVISIBLE else View.VISIBLE
        }

        // Click listeners for selection
        holder.itemRoot.setOnClickListener {
            if (selectionManager?.isSelectionActive() == true) {
                selectionManager.toggleSelection(itemName)
                notifyItemChanged(position)
                onSelectionChanged?.invoke(selectionManager.getSelectedItems().size)
                if (!selectionManager.isSelectionActive()) onSelectionCleared?.invoke()
            } else {
                onItemClick?.invoke(itemName)
            }
        }

        holder.itemRoot.setOnLongClickListener {
            selectionManager?.toggleSelection(itemName)
            notifyItemChanged(position)
            onSelectionChanged?.invoke(selectionManager?.getSelectedItems()?.size ?: 0)
            true
        }

        // Background & text color
        holder.itemRoot.setBackgroundResource(
            when {
                isSelected -> R.drawable.selected_bg
                item.important -> R.drawable.list_element_recycler_bg_important
                else -> R.drawable.list_element_recycler_bg
            }
        )
        holder.itemName.setTextColor(
            holder.itemView.context.getColor(if (isSelected || item.important) R.color.white else R.color.black)
        )
    }


    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newItems: List<Pair<String, ShoppingItem>>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun removeItem(itemName: String) {
        val index = items.indexOfFirst { it.first == itemName }
        if (index != -1) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearSelection() {
        selectionManager?.clearSelection()
        onSelectionCleared?.invoke()
        notifyDataSetChanged()
    }

    fun deleteSelectedItems(deleteFromFirestore: (String) -> Unit) {
        val selected = selectionManager?.getSelectedItems() ?: return
        for (itemName in selected) {
            deleteFromFirestore(itemName)
            removeItem(itemName)
        }
        clearSelection()
    }
}
