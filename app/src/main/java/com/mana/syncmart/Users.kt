package com.mana.syncmart

data class Users(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val friendsMap: Map<String, String> = emptyMap()
)

