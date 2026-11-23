# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class dev.maxxing.github.**$$serializer { *; }
-keepclassmembers class dev.maxxing.github.** {
    *** Companion;
}
-keepclasseswithmembers class dev.maxxing.github.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor / OkHttp / Coroutines
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.**
-keepclassmembers class * implements android.content.BroadcastReceiver {
    public <init>(...);
}
-keep class dev.maxxing.github.app.notifications.NotificationSyncWorker { *; }
-keep class dev.maxxing.github.app.notifications.NotificationActionReceiver { *; }

# UniFFI & JNA (Local iroh P2P FFI)
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
-keep class uniffi.local_client_ffi.** { *; }
-keepclassmembers class uniffi.local_client_ffi.** { *; }
-dontwarn java.awt.**
