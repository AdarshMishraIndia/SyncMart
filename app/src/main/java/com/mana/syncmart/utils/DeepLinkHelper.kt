package com.mana.syncmart.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.mana.syncmart.dashboard.ListManagementActivity
import com.mana.syncmart.list.ListActivity

/**
 * Helper object to handle deep link navigation from push notifications
 * Supports:
 * - myapp://list/<listId> -> Navigate to specific shopping list
 * - myapp://dashboard -> Navigate to main dashboard
 */
object DeepLinkHelper {
    private const val TAG = "DeepLinkHelper"
    private const val SCHEME = "myapp"
    private const val HOST_LIST = "list"
    private const val HOST_DASHBOARD = "dashboard"

    /**
     * Handles deep link navigation based on the provided deep link URL
     * @param context Application context
     * @param deepLink Deep link URL (e.g., "myapp://list/abc123" or "myapp://dashboard")
     * @return true if deep link was handled successfully, false otherwise
     */
    fun handleDeepLink(context: Context, deepLink: String?): Boolean {
        if (deepLink.isNullOrEmpty()) {
            Log.w(TAG, "Deep link is null or empty")
            return false
        }

        return try {
            val uri = deepLink.toUri()
            
            // Validate scheme
            if (uri.scheme != SCHEME) {
                Log.w(TAG, "Invalid deep link scheme: ${uri.scheme}. Expected: $SCHEME")
                return false
            }

            when (uri.host) {
                HOST_LIST -> {
                    val listId = uri.lastPathSegment
                    if (listId.isNullOrEmpty()) {
                        Log.w(TAG, "List ID is missing in deep link: $deepLink")
                        return false
                    }
                    navigateToList(context, listId)
                    true
                }
                HOST_DASHBOARD -> {
                    navigateToDashboard(context)
                    true
                }
                else -> {
                    Log.w(TAG, "Unknown deep link host: ${uri.host}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing deep link: $deepLink", e)
            false
        }
    }

    /**
     * Navigates to a specific shopping list
     * @param context Application context
     * @param listId ID of the shopping list to open
     */
    private fun navigateToList(context: Context, listId: String) {
        Log.d(TAG, "Navigating to list: $listId")
        val intent = Intent(context, ListActivity::class.java).apply {
            putExtra("LIST_ID", listId)
            putExtra("from_notification", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    /**
     * Navigates to the main dashboard (ListManagementActivity)
     * @param context Application context
     */
    private fun navigateToDashboard(context: Context) {
        Log.d(TAG, "Navigating to dashboard")
        val intent = Intent(context, ListManagementActivity::class.java).apply {
            putExtra("from_notification", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    /**
     * Validates if a deep link is properly formatted
     * @param deepLink Deep link URL to validate
     * @return true if valid, false otherwise
     */
    fun isValidDeepLink(deepLink: String?): Boolean {
        if (deepLink.isNullOrEmpty()) return false
        
        return try {
            val uri = deepLink.toUri()
            uri.scheme == SCHEME && (uri.host == HOST_LIST || uri.host == HOST_DASHBOARD)
        } catch (_: Exception) {
            false
        }
    }
}
