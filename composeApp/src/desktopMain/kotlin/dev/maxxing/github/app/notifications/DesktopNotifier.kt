package dev.maxxing.github.app.notifications

import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.NotificationThread
import dev.maxxing.github.network.NotificationMonitor
import dev.maxxing.github.ui.i18n.stringsFor

actual object PlatformNotifier {
    @Volatile
    private var currentLanguage: AppLanguage = AppLanguage.DEFAULT

    actual fun ensureInitialized() {
        // No-op on Linux Desktop; notifications use notify-send / D-Bus on demand.
    }

    actual fun updateLanguage(language: AppLanguage) {
        currentLanguage = language
    }

    actual fun showGitHubNotifications(threads: List<NotificationThread>) {
        if (threads.isEmpty()) return
        val strings = stringsFor(currentLanguage)
        if (threads.size <= 3) {
            threads.forEach { thread ->
                val urgency = if (thread.reason in NotificationMonitor.URGENT_REASONS) "critical" else "normal"
                val reasonLabel = strings.notificationReasonLabel(thread.reason)
                sendLinuxNotification(
                    title = "${thread.repoFullName} ($reasonLabel)",
                    body = thread.subjectTitle,
                    urgency = urgency
                )
            }
        } else {
            val preview = threads.take(3).joinToString("\n") { "• ${it.repoFullName}: ${it.subjectTitle}" }
            sendLinuxNotification(
                title = strings.osNotifBatchTitle(threads.size),
                body = preview,
                urgency = "normal"
            )
        }
    }

    actual fun showTestNotification(): Boolean {
        val strings = stringsFor(currentLanguage)
        return sendLinuxNotification(
            title = strings.osNotifDesktopTestTitle(AppBranding.APP_NAME),
            body = strings.osNotifDesktopTestBody,
            urgency = "normal"
        )
    }

    actual fun syncBackgroundSchedule(enabled: Boolean) {
        // Desktop foreground coroutine poller manages polling interval automatically.
    }

    private fun sendLinuxNotification(title: String, body: String, urgency: String): Boolean {
        return runCatching {
            val pb = ProcessBuilder(
                "notify-send",
                "-a", AppBranding.APP_NAME,
                "-u", urgency,
                "-i", "dialog-information",
                "--",
                title,
                body
            )
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
            pb.environment().remove("LD_LIBRARY_PATH")
            val proc = pb.start()
            proc.outputStream.close()
            true
        }.getOrDefault(false)
    }
}
