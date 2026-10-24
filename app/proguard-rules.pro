# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep line/source mapping for better crash diagnostics.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# TDLib Java bindings & JNI bridge
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }
-keepclasseswithmembernames class org.drinkless.tdlib.** {
    native <methods>;
}

# libmpv bindings and native JNI bridge
-keep class dev.jdtech.mpv.** { *; }
-keepclassmembers class dev.jdtech.mpv.** { *; }
-keepclasseswithmembernames class dev.jdtech.mpv.** {
    native <methods>;
}
-keep class is.xyz.mpv.** { *; }
-keepclassmembers class is.xyz.mpv.** { *; }
-keepclasseswithmembernames class is.xyz.mpv.** {
    native <methods>;
}
-dontwarn dev.jdtech.mpv.**
-dontwarn is.xyz.mpv.**

# All JNI native methods across the app
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve app entry points and components.
-keep class com.vaydelux.bousygram.app.BousygramApp { *; }
-keep class com.vaydelux.bousygram.app.MainActivity { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Ktor & Netty loopback server
-dontwarn io.netty.internal.tcnative.**
-dontwarn java.lang.management.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.eclipse.jetty.npn.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.netty.**
-keep class io.netty.** { *; }
-keepclassmembers class io.netty.** { *; }

# Coil image loading
-dontwarn coil.**
-keep class coil.** { *; }

# Room database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Baseline Profile & ProfileInstaller
-dontwarn androidx.profileinstaller.**
-keep class androidx.profileinstaller.** { *; }

# Serializable models
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

