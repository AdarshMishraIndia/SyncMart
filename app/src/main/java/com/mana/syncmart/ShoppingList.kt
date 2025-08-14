package com.mana.syncmart

import com.google.firebase.Timestamp

data class ShoppingItem(
    val addedBy: String = "",
    val addedAt: Timestamp? = null,
    val important: Boolean = false,
    val pending: Boolean = true
)

data class ShoppingList(
    val id: String = "",
    val listName: String = "",
    val accessEmails: List<String> = emptyList(),
    val owner: String = "",
    val items: Map<String, ShoppingItem> = emptyMap(),
    val createdAt: Timestamp? = null,
    var position: Int = 0
)
