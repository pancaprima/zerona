package sh.zeron.zerona.fcm

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
    private const val SESSION_NOTIFICATION_ID = 1

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

        val notificationId = chatId?.hashCode() ?: SESSION_NOTIFICATION_ID
        val launchIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                chatId?.let { putExtra("chatId", it) }
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
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

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
