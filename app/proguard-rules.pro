# AegisPass HCE R8 Full Mode Shrinking & Security Rules
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Strip all android.util.Log calls in Release builds (NFR 6.3 Log Sanitization)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Keep SQLCipher native bridges
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Keep Room Database Entities & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepattributes *Annotation*

# Keep HostApduService entry point
-keep class com.aegispass.hce.data.hce.AegisHostApduService { *; }
-keep class com.aegispass.hce.data.hce.AegisQuickTileService { *; }
