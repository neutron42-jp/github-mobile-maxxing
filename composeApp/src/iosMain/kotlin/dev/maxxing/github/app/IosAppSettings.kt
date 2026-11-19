@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.maxxing.github.app

import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Opens this app's page in the iOS Settings app (e.g. to re-enable notifications). Returns true when requested. */
internal fun openIosAppSettings(): Boolean {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return false
    fun open() {
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>()) { }
    }
    if (NSThread.isMainThread) {
        open()
    } else {
        dispatch_async(dispatch_get_main_queue()) { open() }
    }
    return true
}
