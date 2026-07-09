# Keep only app entry points
-keep class willyshare.spark.MainActivity { *; }
-keep class willyshare.spark.service.SparkTransferService { *; }

# Keep Android components
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver