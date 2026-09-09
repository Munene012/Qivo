# ============================================================================
# QIVO PRODUCTION ANTI-DECOMPILATION & MAXIMUM OBFUSCATION RULES
# ============================================================================

# 1. Anti-Reverse Engineering & Source Stripping
-renamesourcefileattribute ""
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable,!Synthetic,!MethodParameters,!Deprecated

# 2. Strip Android Logging & Debug Prints in Release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int println(...);
}

# 3. Kotlin & Jetpack Compose Rules
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
    @androidx.compose.runtime.ReadOnlyComposable *;
}
-dontwarn androidx.compose.**
-keep class androidx.compose.material.icons.** { *; }

# 4. Serialization & Network Models Keep Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers enum * { *; }
-keep,allowobfuscation,allowshrinking class com.example.data.** { *; }
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 5. OkHttp, Retrofit, Moshi, & Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn kotlinx.coroutines.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# 6. Firebase & Google Play Services
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# 7. WebViews & JavaScript Interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 8. Voice & Call Engines (Tencent, Zego, CameraX)
-keep class com.example.calling.** { *; }
-dontwarn androidx.camera.**


