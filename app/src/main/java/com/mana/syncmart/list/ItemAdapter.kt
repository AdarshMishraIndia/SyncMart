package com.mana.syncmart.list

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mana.syncmart.R
import com.mana.syncmart.databinding.ListElementRecyclerLayoutBinding
import com.mana.syncmart.dataclass.ShoppingItem

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
        val itemAddedAt: TextView = binding.itemAddedAt
        val itemRoot = binding.root
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListElementRecyclerLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (itemId, item) = items[position]
        holder.itemName.text = item.name

        // Show formatted addedAt in the lower right
        holder.itemAddedAt.text = item.formattedDate

        // Star button
        holder.btnStar.setImageResource(if (item.important) R.drawable.ic_star_important else R.drawable.ic_star)
        holder.btnStar.setOnClickListener { onStarClick?.invoke(position) }

        // Tick button
        holder.btnTick.setImageResource(R.drawable.ic_tick)
        holder.btnTick.setOnClickListener { onTickClick?.invoke(itemId) }

        // Info button
        holder.btnInfo.setOnClickListener { onInfoClick?.invoke(item, item.name) }

        // Selection & finished mode visibility
        val isSelected = selectionManager?.isSelected(itemId) == true
        if (isFinishedMode) {
            holder.btnStar.visibility = View.GONE
            holder.btnTick.visibility = View.GONE
            (holder.btnInfo.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.END
        } else {
            if (isSelected) {
                holder.btnStar.visibility = View.INVISIBLE
                holder.btnTick.visibility = View.INVISIBLE
                holder.btnInfo.visibility = View.INVISIBLE
            } else {
                holder.btnStar.visibility = View.VISIBLE
                holder.btnTick.visibility = View.VISIBLE
                holder.btnInfo.visibility = View.VISIBLE
            }
        }

        // Click listeners for selection
        holder.itemRoot.setOnClickListener {
            if (selectionManager?.isSelectionActive() == true) {
                selectionManager.toggleSelection(itemId)
                notifyItemChanged(position)
                onSelectionChanged?.invoke(selectionManager.getSelectedItems().size)
                if (!selectionManager.isSelectionActive()) onSelectionCleared?.invoke()
            } else {
                onItemClick?.invoke(itemId)
            }
        }

        holder.itemRoot.setOnLongClickListener {
            selectionManager?.toggleSelection(itemId)
            notifyItemChanged(position)
            onSelectionChanged?.invoke(selectionManager?.getSelectedItems()?.size ?: 0)
            true
        }

        // Background & text color
        when {
            isFinishedMode -> {
                // Neutral look in Finished tab
                holder.itemRoot.setBackgroundResource(R.drawable.list_element_recycler_bg) // default bg
                holder.itemName.setTextColor(holder.itemView.context.getColor(R.color.black))
                holder.itemAddedAt.setTextColor(holder.itemView.context.getColor(R.color.black))
            }
            isSelected -> {
                holder.itemRoot.setBackgroundResource(R.drawable.selected_bg)
                holder.itemName.setTextColor(holder.itemView.context.getColor(R.color.white))
                holder.itemAddedAt.setTextColor(holder.itemView.context.getColor(R.color.white))
            }
            item.important -> {
                holder.itemRoot.setBackgroundResource(R.drawable.list_element_recycler_bg_important)
                holder.itemName.setTextColor(holder.itemView.context.getColor(R.color.white))
                holder.itemAddedAt.setTextColor(holder.itemView.context.getColor(R.color.white))
            }
            else -> {
                holder.itemRoot.setBackgroundResource(R.drawable.list_element_recycler_bg) // normal bg
                holder.itemName.setTextColor(holder.itemView.context.getColor(R.color.black))
                holder.itemAddedAt.setTextColor(holder.itemView.context.getColor(R.color.black))
            }
        }
    }


    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newItems: List<Pair<String, ShoppingItem>>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun removeItem(itemId: String) {
        val index = items.indexOfFirst { it.first == itemId }
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
        for (itemId in selected) {
            deleteFromFirestore(itemId)
            removeItem(itemId)
        }
        clearSelection()
    }
}
