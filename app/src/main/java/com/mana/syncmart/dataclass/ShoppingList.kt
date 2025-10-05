package com.mana.syncmart.dataclass

import com.google.firebase.Timestamp

data class ShoppingList(
    val id: String = "",
    val listName: String = "",
    val accessEmails: List<String> = emptyList(),
    val owner: String = "",
    val items: Map<String, ShoppingItem> = emptyMap(),
    val createdAt: Timestamp = Timestamp.now(),
    var position: Int = 0
)