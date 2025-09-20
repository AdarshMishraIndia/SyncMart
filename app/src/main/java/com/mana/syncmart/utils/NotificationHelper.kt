package com.mana.syncmart.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.mana.syncmart.R
import com.mana.syncmart.dashboard.ListManagementActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var notificationId = 0

    init {
        createNotificationChannel()
    }

    /**
     * Shows a simple notification that opens the main activity when tapped
     * @param title The title of the notification
     * @param message The message to display in the notification
     * @param targetId Optional target fragment to navigate to in the main activity
     */
    fun showNotification(
        title: String,
        message: String,
        targetId: String? = null
    ) {
        // Create an intent that opens the main activity
        val intent = Intent(context, ListManagementActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_notification", true)
            targetId?.let { putExtra("target_fragment", it) }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId++,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build and show the notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_syncmart)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Delete any existing channel with the same ID to ensure updates take effect
            notificationManager.deleteNotificationChannel(CHANNEL_ID)
            
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            
            val soundUri =
                "android.resource://${context.packageName}/${R.raw.notification_sound}".toUri()
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(false)  // Disable vibration
                setShowBadge(true)      // Show app icon badge
                setSound(soundUri, audioAttributes)
                enableLights(true)      // Enable notification LED if available
                lightColor = context.getColor(R.color.purple_500) // Use your app's primary color
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "syncmart_notification_channel"
    }
}
