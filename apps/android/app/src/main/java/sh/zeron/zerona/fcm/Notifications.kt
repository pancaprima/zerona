package sh.zeron.zerona.fcm

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import sh.zeron.zerona.MainActivity
import sh.zeron.zerona.R

object FcmTokenStore {
    private const val PREFS = "zerona_fcm"
    private const val KEY_TOKEN = "token"

    @Volatile
    var onTokenSaved: ((String) -> Unit)? = null

    fun getToken(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)

    fun saveToken(
        context: Context,
        token: String,
    ) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
        onTokenSaved?.invoke(token)
    }
}

object Notifications {
    const val SESSIONS_CHANNEL_ID = "sessions"
    const val SESSION_NOTIFICATION_ID = 1

    fun ensureSessionsChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(SESSIONS_CHANNEL_ID) != null) return
        val channel =
            NotificationChannel(
                SESSIONS_CHANNEL_ID,
                context.getString(R.string.app_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        manager.createNotificationChannel(channel)
    }

    fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun shouldRequestPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return !hasPostNotificationsPermission(context)
    }

    fun requestPostNotificationsPermission(
        activity: Activity,
        launcher: ActivityResultLauncher<String>,
    ) {
        if (!shouldRequestPostNotificationsPermission(activity)) return
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun showSessionNotification(
        context: Context,
        title: String,
        body: String,
        chatId: String? = null,
    ) {
        ensureSessionsChannel(context)
        if (!hasPostNotificationsPermission(context)) return

        val validated = chatId?.let { NotificationChatId.validate(it) }
        val notificationTag = validated?.let { SessionNotificationIdentity.notificationTag(it) }
        val notificationId =
            validated?.let { SessionNotificationIdentity.notificationId(it) }
                ?: SESSION_NOTIFICATION_ID
        val launchIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                validated?.let { id ->
                    putExtra("chatId", id)
                    SessionNotificationIdentity.pendingIntentUriString(id)?.let { data = Uri.parse(it) }
                }
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(context, SESSIONS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_zerona)
                .setColor(ContextCompat.getColor(context, R.color.zerona_primary))
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

        val manager = NotificationManagerCompat.from(context)
        if (notificationTag != null) {
            manager.notify(notificationTag, notificationId, notification)
        } else {
            manager.notify(notificationId, notification)
        }
    }

    fun cancelSessionNotification(
        context: Context,
        chatId: String,
    ) {
        val validated = NotificationChatId.validate(chatId) ?: return
        val manager = NotificationManagerCompat.from(context)
        val tag = SessionNotificationIdentity.notificationTag(validated) ?: return
        val id = SessionNotificationIdentity.notificationId(validated)
        manager.cancel(tag, id)
        SessionNotificationIdentity.legacyUntaggedNotificationId(validated)?.let { legacyId ->
            manager.cancel(legacyId)
        }
    }

    /**
     * Clears tray items for chats confirmed read in a synced workspace snapshot.
     * Never cancels notifications whose chat id is absent from [unseenByChatId].
     */
    fun reconcileReadSessionNotifications(
        context: Context,
        unseenByChatId: Map<String, Boolean>,
        workspaceSynced: Boolean,
    ) {
        if (!workspaceSynced) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            for (status in manager.activeNotifications) {
                val tag = status.tag
                if (tag != null && tag.startsWith(SessionNotificationIdentity.TAG_PREFIX)) {
                    val chatId = tag.removePrefix(SessionNotificationIdentity.TAG_PREFIX)
                    if (SessionNotificationReconcilePolicy.shouldCancelForChat(chatId, unseenByChatId, true)) {
                        manager.cancel(tag, status.id)
                    }
                }
            }
        }
        for ((chatId, unseen) in unseenByChatId) {
            if (!unseen) {
                cancelSessionNotification(context, chatId)
            }
        }
    }
}
