package com.mana.syncmart

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mana.syncmart.databinding.ListRecyclerLayoutBinding
import android.view.View

class ListAdapter(
    private var list: List<ShoppingList>,
    private val onDeleteClicked: (String) -> Unit,
    private val onModifyClicked: (ShoppingList) -> Unit, // Callback for modifying the list
    private val onListClicked: (ShoppingList) -> Unit, // ✅ Callback for list click
    private val loggedInUserEmail: String // Add logged-in user email
) : RecyclerView.Adapter<ListAdapter.ViewHolder>() {

    class ViewHolder(binding: ListRecyclerLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        val buttonName: TextView = binding.buttonName
        val btnDelete: ImageButton = binding.btnDelete
        val btnSettings: ImageButton = binding.btnSettings
        val notifBubble: TextView = binding.notifBubble
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListRecyclerLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val listItem = list[position]
        holder.buttonName.text = listItem.listName.ifEmpty { "Unnamed List" }

        // ✅ Show or hide notification bubble based on pendingItems
        if (listItem.pendingItems.isNotEmpty()) {
            holder.notifBubble.visibility = View.VISIBLE
            holder.notifBubble.text = listItem.pendingItems.size.toString() // Show count
        } else {
            holder.notifBubble.visibility = View.GONE
        }

        // Show buttons only if logged-in user is the owner
        if (listItem.owner == loggedInUserEmail) {
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnSettings.visibility = View.VISIBLE
        } else {
            holder.btnDelete.visibility = View.GONE
            holder.btnSettings.visibility = View.GONE
        }

        // ✅ Handle list item click to navigate to ListActivity
        holder.buttonName.setOnClickListener {
            onListClicked(listItem)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClicked(listItem.listName)
        }

        holder.btnSettings.setOnClickListener {
            onModifyClicked(listItem)
        }
    }


    override fun getItemCount(): Int = list.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: List<ShoppingList>) {
        list = newList
        notifyDataSetChanged()
    }
}
