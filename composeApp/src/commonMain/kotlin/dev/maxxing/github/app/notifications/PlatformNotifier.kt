package dev.maxxing.github.app.notifications

import dev.maxxing.github.model.AppLanguage
import dev.maxxing.github.model.NotificationThread

expect object PlatformNotifier {
    fun ensureInitialized()
    fun updateLanguage(language: AppLanguage)
    fun showGitHubNotifications(threads: List<NotificationThread>)
    fun showTestNotification(): Boolean
    fun syncBackgroundSchedule(enabled: Boolean)
}
