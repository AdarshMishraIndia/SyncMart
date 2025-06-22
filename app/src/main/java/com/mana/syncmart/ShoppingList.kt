package com.mana.syncmart

data class ShoppingList(
    val id: String = "",
    val listName: String = "",
    val accessEmails: List<String> = emptyList(),
    val owner: String = "",
    val pendingItems: Map<String, Boolean> = emptyMap(),
    val finishedItems: List<String> = emptyList(),
    var position: Int = 0
)
