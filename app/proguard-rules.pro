# Proguard rules for Auvra
-keep class com.auvra.app.** { *; }

# Strip Debug and Verbose logging in release builds to improve performance and security.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
