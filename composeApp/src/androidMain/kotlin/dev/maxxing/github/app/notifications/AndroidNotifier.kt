package dev.maxxing.github.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.maxxing.github.app.shared.R
import dev.maxxing.github.auth.AndroidAuthContextHolder
import dev.maxxing.github.auth.DeviceFlowClient
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.NotificationThread
import dev.maxxing.github.network.GitHubApiException
import dev.maxxing.github.network.GitHubRepository
import dev.maxxing.github.network.NotificationMonitor
import dev.maxxing.github.network.splitForOsDisplay
import dev.maxxing.github.ui.i18n.AppStrings
import dev.maxxing.github.ui.i18n.stringsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private const val CHANNEL_URGENT = "maxxing_urgent"
private const val CHANNEL_ACTIVITY = "maxxing_activity"
private const val WORK_NAME = "github_maxxing_notification_sync"

private const val ACTION_MARK_READ = "dev.maxxing.github.app.ACTION_MARK_READ"
private const val ACTION_MARK_DONE = "dev.maxxing.github.app.ACTION_MARK_DONE"
private const val EXTRA_THREAD_ID = "thread_id"
private const val EXTRA_NOTIF_ID = "notif_id"
private const val OVERFLOW_SUMMARY_NOTIF_ID = 9002

actual object PlatformNotifier {
    @Volatile
    private var currentLanguage: AppLanguage = AppLanguage.DEFAULT

    private fun currentStrings(): AppStrings = stringsFor(currentLanguage)

    actual fun updateLanguage(language: AppLanguage) {
        if (currentLanguage != language) {
            currentLanguage = language
            ensureInitialized()
        }
    }

    actual fun ensureInitialized() {
        val context = runCatching { AndroidAuthContextHolder.requireContext() }.getOrNull() ?: return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val strings = currentStrings()

        val urgentChannel = NotificationChannel(
            CHANNEL_URGENT,
            strings.osNotifUrgentChannelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = strings.osNotifUrgentChannelDesc
        }

        val activityChannel = NotificationChannel(
            CHANNEL_ACTIVITY,
            strings.osNotifActivityChannelName,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = strings.osNotifActivityChannelDesc
        }

        manager.createNotificationChannel(urgentChannel)
        manager.createNotificationChannel(activityChannel)
    }

    actual fun showGitHubNotifications(threads: List<NotificationThread>) {
        if (threads.isEmpty()) return
        val context = runCatching { AndroidAuthContextHolder.requireContext() }.getOrNull() ?: return
        if (!canPostNotifications(context)) return
        ensureInitialized()

        val strings = currentStrings()
        val nm = NotificationManagerCompat.from(context)
        val batch = splitForOsDisplay(threads)
        batch.individual.forEach { thread ->
            val notifId = thread.id.hashCode()
            val isUrgent = thread.reason in NotificationMonitor.URGENT_REASONS
            val channelId = if (isUrgent) CHANNEL_URGENT else CHANNEL_ACTIVITY

            val contentIntent = buildOpenThreadIntent(context, thread, notifId)
            val markReadIntent = buildActionIntent(context, ACTION_MARK_READ, thread.id, notifId)
            val markDoneIntent = buildActionIntent(context, ACTION_MARK_DONE, thread.id, notifId)

            val reasonLabel = strings.notificationReasonLabel(thread.reason)
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_maxxing)
                .setContentTitle("${thread.repoFullName} • $reasonLabel")
                .setContentText(thread.subjectTitle)
                .setStyle(NotificationCompat.BigTextStyle().bigText(thread.subjectTitle))
                .setAutoCancel(true)
                .setPriority(
                    if (isUrgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
                )
                .setContentIntent(contentIntent)
                .addAction(0, strings.osNotifMarkReadAction, markReadIntent)
                .addAction(0, strings.doneAction, markDoneIntent)
                .build()

            nm.notify(notifId, notification)
        }

        // Represent threads beyond the individual cap with one summary instead of dropping them.
        if (batch.overflow.isNotEmpty()) {
            val inboxStyle = NotificationCompat.InboxStyle()
            batch.overflow.take(5).forEach { inboxStyle.addLine("${it.repoFullName}: ${it.subjectTitle}") }
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val summary = NotificationCompat.Builder(context, CHANNEL_ACTIVITY)
                .setSmallIcon(R.drawable.ic_stat_maxxing)
                .setContentTitle(strings.osNotifOverflowTitle(batch.overflow.size))
                .setContentText(strings.osNotifOverflowBody)
                .setStyle(inboxStyle)
                .setAutoCancel(true)
                .apply {
                    if (launchIntent != null) {
                        setContentIntent(
                            PendingIntent.getActivity(
                                context,
                                OVERFLOW_SUMMARY_NOTIF_ID,
                                launchIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                        )
                    }
                }
                .build()
            nm.notify(OVERFLOW_SUMMARY_NOTIF_ID, summary)
        }
    }

    actual fun showTestNotification(): Boolean {
        val context = runCatching { AndroidAuthContextHolder.requireContext() }.getOrNull() ?: return false
        if (!canPostNotifications(context)) return false
        ensureInitialized()
        val strings = currentStrings()

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                9001,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_URGENT)
            .setSmallIcon(R.drawable.ic_stat_maxxing)
            .setContentTitle(strings.osNotifTestReadyTitle(AppBranding.APP_NAME))
            .setContentText(strings.osNotifTestReadyBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .build()

        NotificationManagerCompat.from(context).notify(9001, notification)
        return true
    }

    actual fun syncBackgroundSchedule(enabled: Boolean) {
        val context = runCatching { AndroidAuthContextHolder.requireContext() }.getOrNull() ?: return
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<NotificationSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    actual fun areOsNotificationsBlocked(): Boolean {
        val context = runCatching { AndroidAuthContextHolder.requireContext() }.getOrNull() ?: return true
        return !canPostNotifications(context)
    }

    actual fun openOsNotificationSettings(): Boolean {
        val context = runCatching { AndroidAuthContextHolder.requireContext() }.getOrNull() ?: return false
        return runCatching {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun canPostNotifications(context: Context): Boolean =
        hasNotificationPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildOpenThreadIntent(
        context: Context,
        thread: NotificationThread,
        requestCode: Int
    ): PendingIntent {
        val num = thread.subjectNumber
        val intent = if (num != null) {
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("githubmaxxing://thread/${thread.repoOwner}/${thread.repoName}/$num")
            ).apply {
                setPackage(context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildActionIntent(
        context: Context,
        action: String,
        threadId: String,
        notifId: Int
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_NOTIF_ID, notifId)
        }
        val code = (action + threadId).hashCode()
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class NotificationSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        AndroidAuthContextHolder.init(applicationContext)
        // The worker owns its HTTP clients; close them so periodic runs do not leak them.
        val authHttpClient = DeviceFlowClient.defaultAuthHttpClient()
        try {
            val sessionManager = SessionManager(deviceFlowClient = DeviceFlowClient(authHttpClient))
            sessionManager.initialize()
            PlatformNotifier.updateLanguage(sessionManager.themePreferences.value.language)
            if (sessionManager.session.value == null) return Result.success()
            if (!sessionManager.notificationPreferences.value.osNotificationsEnabled) return Result.success()

            val repository = GitHubRepository(sessionManager)
            try {
                val monitor = NotificationMonitor(sessionManager, repository) {
                    !PlatformNotifier.areOsNotificationsBlocked()
                }
                val check = monitor.checkForNewNotifications()
                check.onSuccess { res ->
                    if (res.newlyArrivedForOs.isNotEmpty()) {
                        PlatformNotifier.showGitHubNotifications(res.newlyArrivedForOs)
                    }
                }
                val error = check.exceptionOrNull()
                return when {
                    error == null -> Result.success()
                    // Retrying cannot fix an invalid/expired token; the next periodic run tries again.
                    (error as? GitHubApiException)?.statusCode == 401 -> Result.success()
                    else -> Result.retry()
                }
            } finally {
                repository.close()
            }
        } finally {
            authHttpClient.close()
        }
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getStringExtra(EXTRA_THREAD_ID) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        val action = intent.action ?: return

        NotificationManagerCompat.from(context).cancel(notifId)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                AndroidAuthContextHolder.init(context.applicationContext)
                val sessionManager = SessionManager()
                sessionManager.initialize()
                if (sessionManager.session.value != null) {
                    val repo = GitHubRepository(sessionManager)
                    when (action) {
                        ACTION_MARK_READ -> repo.markNotificationRead(threadId)
                        ACTION_MARK_DONE -> repo.markNotificationDone(threadId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
