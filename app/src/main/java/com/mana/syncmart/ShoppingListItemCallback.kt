package com.mana.syncmart

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil

class ShoppingListItemCallback : DiffUtil.ItemCallback<ShoppingList>() {
    override fun areItemsTheSame(oldItem: ShoppingList, newItem: ShoppingList): Boolean {
        return oldItem.id == newItem.id
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: ShoppingList, newItem: ShoppingList): Boolean {
        return oldItem.listName == newItem.listName &&
                oldItem.position == newItem.position &&
                oldItem.pendingItems == newItem.pendingItems &&
                oldItem.finishedItems == newItem.finishedItems
    }
}
