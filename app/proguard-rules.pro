# Keep only app entry points
-keep class com.willyshare.willykez.MainActivity { *; }
-keep class com.willyshare.willykez.service.SparkTransferService { *; }

# Keep Android components
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver