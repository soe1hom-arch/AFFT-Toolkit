# Keep all binaries in assets
-keep class com.afft.app.** { *; }

# Jetpack Compose ProGuard rules
# Fix lock verification warnings on SnapshotStateList
-keepclassmembers class androidx.compose.runtime.snapshots.SnapshotStateList {
    *** mutate(...);
    *** update(...);
    *** conditionalUpdate(...);
}
-keep class androidx.compose.runtime.snapshots.SnapshotStateList { *; }

# Keep Compose internal classes
-keep class androidx.compose.** { *; }

# Keep all native binary paths
-keepclassmembers class com.afft.app.util.BinaryManager {
    *** getBinaryPath(...);
    *** getBinaryDirPath(...);
}

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
