package com.mana.syncmart.fcm

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mana.syncmart.R
import com.mana.syncmart.utils.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class SyncMartMessagingService : FirebaseMessagingService() {
    private val tag = "SyncMartMessaging"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(tag, "New FCM token received: $token")
        
        // Get current user
        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.email.isNullOrEmpty()) {
            // Update token in Firestore using email as document ID
            updateTokenInFirestore(currentUser.email!!, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(tag, "From: ${remoteMessage.from}")
        Log.d(tag, "Raw data: ${remoteMessage.data}")
        
        // Extract all data fields
        val data = remoteMessage.data
        val type = data["type"]
        val targetId = data["target_id"]
        val listId = data["list_id"] // Check for list_id as well
        
        Log.d(tag, "Extracted data - Type: $type, TargetID: $targetId, ListID: $listId")
        
        // If we have a notification payload, use it
        remoteMessage.notification?.let { notification ->
            val title = notification.title ?: getString(R.string.app_name)
            val message = notification.body ?: ""
            
            Log.d(tag, "Notification received - Title: $title, Message: $message")
            
            // Determine the best target ID to use (prefer list_id if available)
            val effectiveTargetId = listId ?: targetId
            
            // If we have a list_id but no type, assume it's a NEW_ITEM_ADDED
            val effectiveType = if (effectiveTargetId != null && type == null) "NEW_ITEM_ADDED" else type
            
            Log.d(tag, "Sending notification - Type: $effectiveType, Target: $effectiveTargetId")
            
            notificationHelper.showNotification(
                title = title,
                message = message,
                targetId = effectiveTargetId
            )
        } ?: run {
            Log.d(tag, "No notification payload, only data message")
        }
    }

    /**
     * Updates the FCM token in Firestore for the given user email
     * @param userEmail User's email to be used as document ID
     * @param token FCM token to be saved
     */
    internal fun updateTokenInFirestore(userEmail: String, token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userRef = firestore.collection("Users").document(userEmail)
                
                // Get current tokens array or initialize if it doesn't exist
                val userDoc = userRef.get().await()
                val tokens = userDoc.get("fcmTokens")
                val currentTokens = when (tokens) {
                    is List<*> -> tokens.filterIsInstance<String>().toMutableList()
                    else -> mutableListOf()
                }
                
                // Add new token if not already present
                if (!currentTokens.contains(token)) {
                    currentTokens.add(token)
                    userRef.update("fcmTokens", currentTokens).await()
                    Log.d(tag, "FCM token updated for user: $userEmail")
                } else {
                    Log.d(tag, "FCM token already exists for user: $userEmail")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error updating FCM token in Firestore", e)
            }
        }
    }

    companion object
}
