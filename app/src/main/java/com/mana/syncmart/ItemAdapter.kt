package com.mana.syncmart

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mana.syncmart.databinding.ListElementRecyclerLayoutBinding

class ItemAdapter(
    private var items: MutableList<String>,
    private val onDeleteClicked: (String) -> Unit,
    private val onItemChecked: (String) -> Unit,
    private val showButtons: Boolean
) : RecyclerView.Adapter<ItemAdapter.ViewHolder>() {

    class ViewHolder(binding: ListElementRecyclerLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        val itemName: TextView = binding.buttonName
        val btnDelete: ImageButton = binding.btnDelete
        val btnSettings: ImageButton = binding.btnSettings
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListElementRecyclerLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.itemName.text = item

        if (showButtons) {
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnSettings.visibility = View.VISIBLE
        } else {
            holder.btnDelete.visibility = View.GONE
            holder.btnSettings.visibility = View.GONE
        }

        holder.btnDelete.setOnClickListener { onDeleteClicked(item) }
        holder.btnSettings.setOnClickListener { onItemChecked(item) }
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newItems: List<String>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}