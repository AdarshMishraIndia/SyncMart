package com.mana.syncmart.dataclass

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ShoppingItem(
    val name: String = "",
    val addedBy: String = "",
    val addedAt: String = "",        // ISO 8601 — when item was added (pending) or finished
    val important: Boolean = false,
    val pending: Boolean = true,
    val active: Boolean = true,      // false = soft-deleted
    val lastModified: Timestamp? = null  // null for legacy items; backfilled on first write
) {
    // Helper property to get formatted date string in 'hh:mm a dd MMMM yyyy' format
    val formattedDate: String
        get() {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(addedAt) ?: return addedAt
                SimpleDateFormat("hh:mm a dd MMMM yyyy", Locale.getDefault()).format(date)
            } catch (_: Exception) {
                addedAt
            }
        }
}