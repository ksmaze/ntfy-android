package io.heckel.ntfy.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import io.heckel.ntfy.R
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.msg.ApiService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class NotificationListenerService : NotificationListenerService() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val notification = sbn.notification
        val extras = notification.extras
        val packageName = sbn.packageName
        val ctx = applicationContext
        val repository = Repository.getInstance(ctx)
        val topic = repository.getSmsTopic()

        if (packageName == "com.google.android.apps.messaging" && !topic.isNullOrBlank()) {
            val api = ApiService()
            val baseUrl = ctx.getString(R.string.app_base_url)
            val title = extras.getString(Notification.EXTRA_TITLE,"")
            val text = extras.getString(Notification.EXTRA_TEXT,"")
            GlobalScope.launch(Dispatchers.IO) {
                val user = repository.getUser(baseUrl) // May be null
                try {
                    api.publish(
                        baseUrl = baseUrl,
                        topic = topic,
                        user = user,
                        message = text,
                        title = title,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to publish message: ${e.message}", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "NotificationListenerService"
    }
}