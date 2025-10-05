package com.mana.syncmart.dashboard

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil
import com.mana.syncmart.dataclass.ShoppingList

@SuppressLint("DiffUtilEquals")
class ShoppingListItemCallback : DiffUtil.ItemCallback<ShoppingList>() {
    override fun areItemsTheSame(oldItem: ShoppingList, newItem: ShoppingList): Boolean {
        return oldItem.id == newItem.id
    }
    override fun areContentsTheSame(oldItem: ShoppingList, newItem: ShoppingList): Boolean {
        return oldItem.listName == newItem.listName &&
                oldItem.position == newItem.position &&
                oldItem.items == newItem.items
    }
}