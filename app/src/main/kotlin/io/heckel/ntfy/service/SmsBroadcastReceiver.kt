package io.heckel.ntfy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.msg.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.heckel.ntfy.R

class SmsBroadcastReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val repository = Repository.getInstance(context)
        val topic = repository.getSmsTopic()
        if (topic.isNullOrBlank()) {
            Log.d(TAG, "SMS topic not configured, ignoring SMS")
            return
        }

        val api = ApiService()
        val baseUrl = context.getString(R.string.app_base_url)


        for (message in messages) {
            val sender = message.originatingAddress ?: continue
            val body = message.messageBody ?: continue
            val contactName = getContactName(context, sender)
            val title = if (contactName != null) "SMS from $contactName ($sender)" else "SMS from $sender"

            scope.launch {
                val user = repository.getUser(baseUrl) // May be null
                try {
                    api.publish(
                        baseUrl = baseUrl,
                        topic = topic,
                        user = user,
                        message = body,
                        title = title,
                        tags = listOf("sms"),
                    )
                    Log.d(TAG, "Forwarded SMS from $sender to $topic")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to forward SMS to $topic", e)
                }
            }
        }
    }

    private fun getContactName(context: Context, phoneNumber: String): String? {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }

    companion object {
        private const val TAG = "NtfySmsReceiver"
    }
}
