# Sparks - R8 / ProGuard rules
#
# isMinifyEnabled + isShrinkResources are on for release builds. Debug builds are
# never shrunk, so none of this affects local development - only `assembleRelease`.
#
# General rule of thumb applied here: let R8 shrink/obfuscate everything it can
# reason about statically, and only -keep the handful of classes that libraries or
# our own code reach via reflection.

# ---------------------------------------------------------------------------
# Keep line numbers so release crash stack traces stay human-readable.
# ---------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations - needed for Room, and for anything inspecting them at runtime.
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# ---------------------------------------------------------------------------
# Kotlin metadata / coroutines
# ---------------------------------------------------------------------------
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlinx.coroutines.CoroutineExceptionHandler { *; }

# ---------------------------------------------------------------------------
# Room - entities/DAOs are referenced directly by generated *_Impl classes at
# compile time (KSP), not via runtime reflection, so this is mostly a safety net
# for the entity/converter classes themselves.
# ---------------------------------------------------------------------------
-keep class willyshare.spark.data.*Entity { *; }
-keep interface willyshare.spark.data.PulseDao { *; }
-keep class willyshare.spark.data.PulseDatabase { *; }
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# Our own model/transfer classes sent across the wire or stored - keep field
# names/order stable in case any future JSON/Parcelable path is added, and so
# these are readable in crash reports.
# ---------------------------------------------------------------------------
-keep class willyshare.spark.net.SendableFile { *; }
-keep class willyshare.spark.net.TransferProgress { *; }
-keep class willyshare.spark.net.FileProgressItem { *; }
-keep class willyshare.spark.net.LocalFileNode { *; }
-keep class willyshare.spark.net.StorageRoot { *; }
-keep class willyshare.spark.net.QrPairing$Payload { *; }

# ---------------------------------------------------------------------------
# ZXing (QR encode/decode) - ships its own consumer rules, but older versions
# have reflection around format readers; keep the whole package defensively.
# ---------------------------------------------------------------------------
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ---------------------------------------------------------------------------
# CameraX - uses reflection to discover the Camera2/CameraPipe implementation.
# ---------------------------------------------------------------------------
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.core.impl.** { *; }
-dontwarn androidx.camera.**

# ---------------------------------------------------------------------------
# WifiP2p / networking - framework classes, but keep our BroadcastReceiver and
# any anonymous inner classes registered with the system intact and reachable.
# ---------------------------------------------------------------------------
-keep class willyshare.spark.net.WifiDirectManager { *; }
-keep class willyshare.spark.net.WifiDirectManager$* { *; }

# ---------------------------------------------------------------------------
# material-kolor (Material You dynamic color) + graphics-shapes - both use
# some reflection/inline-class tricks around Compose; keep defensively since
# they're small libraries with less mainstream R8 testing than AndroidX proper.
# ---------------------------------------------------------------------------
-keep class com.materialkolor.** { *; }
-dontwarn com.materialkolor.**
-keep class androidx.graphics.shapes.** { *; }
-dontwarn androidx.graphics.shapes.**

# ---------------------------------------------------------------------------
# DocumentFile / SAF - reflection-free, but keep defensively since it wraps
# ContentResolver calls that R8 can't fully trace.
# ---------------------------------------------------------------------------
-dontwarn androidx.documentfile.**

# ---------------------------------------------------------------------------
# Foreground service + notification helper - referenced by class name via
# Intent(context, SparkTransferService::class.java) and PendingIntents, which
# R8 already tracks correctly, but keep the entry points explicit and safe.
# ---------------------------------------------------------------------------
-keep class willyshare.spark.service.SparkTransferService { *; }
-keep class willyshare.spark.MainActivity { *; }

# ---------------------------------------------------------------------------
# Compose - AndroidX ships its own consumer ProGuard rules for Compose runtime/
# UI/Material3; nothing extra needed here. Left as a comment for visibility.
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Firebase BOM is on the classpath as a platform import only (no Firebase SDK
# modules are actually applied - see the commented-out firebase deps in
# build.gradle.kts), so no Firebase-specific keep rules are needed. If a real
# Firebase module (Analytics, Crashlytics, etc.) is added later, re-check this.
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# General Android housekeeping
# ---------------------------------------------------------------------------
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}
