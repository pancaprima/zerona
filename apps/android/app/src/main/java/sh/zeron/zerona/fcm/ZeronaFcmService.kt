package sh.zeron.zerona.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ZeronaFcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        FcmTokenStore.saveToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification
        val title = notification?.title?.takeIf { it.isNotBlank() } ?: return
        val body = notification.body ?: ""
        Notifications.showSessionNotification(
            applicationContext,
            title,
            body,
            message.data["chatId"],
        )
    }
}
