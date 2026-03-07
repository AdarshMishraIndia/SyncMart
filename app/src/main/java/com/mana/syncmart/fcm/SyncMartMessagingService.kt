package com.mana.syncmart.fcm

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mana.syncmart.R
import com.mana.syncmart.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SyncMartMessagingService : FirebaseMessagingService() {
    private val tag = "SyncMartMessaging"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(applicationContext)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.email.isNullOrEmpty()) {
            updateTokenInFirestore(currentUser.email!!, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(tag, "From: ${remoteMessage.from}")

        // 1. Extract Data
        val data = remoteMessage.data
        val deepLink = data["deepLink"] ?: "syncmart://dashboard"

        // 2. Extract Title/Body from Notification object OR Data payload
        val title = remoteMessage.notification?.title ?: data["title"] ?: getString(R.string.app_name)
        val message = remoteMessage.notification?.body ?: data["message"] ?: "New update available"

        // 3. Prevent Duplication:
        // If remoteMessage.notification is NOT null, Firebase automatically
        // shows a notification when the app is in the background.
        // We only manually show it if the app is in the FOREGROUND.
        notificationHelper.showNotification(
            title = title,
            message = message,
            targetId = data["list_id"],
            deepLink = deepLink
        )
    }

    fun updateTokenInFirestore(userEmail: String, token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userRef = firestore.collection("Users").document(userEmail)
                // Use arrayUnion to avoid manual fetching and duplicates
                userRef.update("fcmToken", FieldValue.arrayUnion(token))
                Log.d(tag, "Token synced successfully")
            } catch (e: Exception) {
                Log.e(tag, "Error updating token", e)
            }
        }
    }
}