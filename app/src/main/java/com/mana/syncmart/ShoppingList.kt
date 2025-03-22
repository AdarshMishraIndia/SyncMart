package com.mana.syncmart

data class ShoppingList(
    val id: String = "",
    val listName: String = "",
    val accessEmails: List<String> = emptyList(),
    val owner: String = "",
    val pendingItems: List<String> = emptyList(),
    val finishedItems: List<String> = emptyList()
)
