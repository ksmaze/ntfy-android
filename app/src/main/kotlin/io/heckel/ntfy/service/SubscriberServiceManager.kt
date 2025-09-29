package io.heckel.ntfy.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.*
import io.heckel.ntfy.app.Application
import io.heckel.ntfy.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * This class only manages the SubscriberService, i.e. it starts or stops it.
 * It's used in multiple activities.
 *
 * We are starting the service via a worker and not directly because since Android 7
 * (but officially since Lollipop!), any process called by a BroadcastReceiver
 * (only manifest-declared receiver) is run at low priority and hence eventually
 * killed by Android.
 */
class SubscriberServiceManager(private val context: Context) {
    fun refresh() {
        Log.d(TAG, "Enqueuing work to refresh subscriber service")
        val workManager = WorkManager.getInstance(context)
        val startServiceRequest = OneTimeWorkRequest.Builder(ServiceStartWorker::class.java).build()
        workManager.enqueueUniqueWork(WORK_NAME_ONCE, ExistingWorkPolicy.KEEP, startServiceRequest) // Unique avoids races!
    }

    fun restart() {
        Intent(context, SubscriberService::class.java).also { intent ->
            context.stopService(intent) // Service will auto-restart
        }
    }

    /**
     * Starts or stops the foreground service by figuring out how many instant delivery subscriptions
     * exist. If there's > 0, then we need a foreground service.
     */
    class ServiceStartWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val id = this.id
            if (context.applicationContext !is Application) {
                Log.d(TAG, "ServiceStartWorker: Failed, no application found (work ID: ${id})")
                return Result.failure()
            }
            withContext(Dispatchers.IO) {
                val app = context.applicationContext as Application
                val subscriptionIdsWithInstantStatus = app.repository.getSubscriptionIdsWithInstantStatus()
                val instantSubscriptions = subscriptionIdsWithInstantStatus.toList().filter { (_, instant) -> instant }.size
                val action = SubscriberService.Action.STOP
                val serviceState = SubscriberService.readServiceState(context)
                if (serviceState == SubscriberService.ServiceState.STOPPED && action == SubscriberService.Action.STOP) {
                    return@withContext Result.success()
                }
                Log.d(TAG, "ServiceStartWorker: Starting foreground service with action $action (work ID: ${id})")
                
                // Handle foreground service start based on Android version
                Intent(context, SubscriberService::class.java).also {
                    it.action = action.name
                    
                    try {
                        // For Android 14+ (API 34+), use different approaches based on the situation
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            // Since we're in a WorkManager context (background), we can't directly start 
                            // a foreground service in Android 14+. Instead, we'll start a regular service,
                            // and it will promote itself to foreground in onCreate().
                            context.startService(it)
                        } else {
                            // For older Android versions, we can use startForegroundService
                            ContextCompat.startForegroundService(context, it)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start service: ${e.message}", e)
                    }
                }
            }
            return Result.success()
        }
    }

    companion object {
        const val TAG = "NtfySubscriberMgr"
        const val WORK_NAME_ONCE = "ServiceStartWorkerOnce"

        fun refresh(context: Context) {
            val manager = SubscriberServiceManager(context)
            manager.refresh()
        }
    }
}
