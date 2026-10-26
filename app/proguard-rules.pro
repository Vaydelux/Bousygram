# Project-specific ProGuard / R8 rules for Bousygram

# 1. Preserve SourceFile and LineNumberTable attributes for de-obfuscation and crash diagnostics
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# 2. Room Database:
# Room creates AppDatabase_Impl reflectively via its parameterless constructor.
# R8 full mode removes <init>() unless explicit member keep rules are defined.
-keep class * extends androidx.room.RoomDatabase {
    public <init>();
    *;
}
-keep class com.vaydelux.bousygram.data.database.AppDatabase_Impl {
    public <init>();
    *;
}
-keep class **.*_Impl extends androidx.room.RoomDatabase {
    public <init>();
    *;
}
-keepclassmembers class * extends androidx.room.RoomDatabase {
    *;
}
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# 3. TDLib (Telegram Database Library) Java bindings & JNI bridge
# TDLib C++ native library makes reflective calls into Java classes, fields, constructors, and callbacks.
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }
-keepclasseswithmembernames class org.drinkless.tdlib.** {
    native <methods>;
}
-dontwarn org.drinkless.tdlib.**

# 4. libmpv Video Player Engine & FFmpeg native JNI bridge
# libmpv interacts with Java/Kotlin via JNI callbacks for events, demuxing, and hardware decoding.
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

# 5. JNI Native Methods across all application packages
-keepclasseswithmembernames class * {
    native <methods>;
}

# 6. Ktor & Netty Local Loopback Streaming Server
# TelegramProxyManager runs an embedded Netty HTTP server on 127.0.0.1.
# Netty relies on internal reflection (sun.misc.Unsafe, PlatformDependent0, shaded JCTools).
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }

-dontwarn io.netty.**
-keep class io.netty.** { *; }
-keepclassmembers class io.netty.** { *; }

# Netty internal reflection and shaded lock-free queues
-keepclasseswithmembernames class io.netty.** {
    native <methods>;
}
-keepclassmembers class io.netty.util.internal.shaded.org.jctools.** {
    *;
}
-keepclassmembers class io.netty.util.internal.PlatformDependent0 {
    *;
}

# ServiceLoader discovery for Coroutines and Ktor ApplicationEngine
-keep class * implements io.ktor.server.engine.ApplicationEngineFactory { *; }
-keep class * implements io.ktor.server.engine.ApplicationEngine { *; }
-keepnames class * implements kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class * implements kotlinx.coroutines.CoroutineExceptionHandler

# Netty optional / non-Android dependencies that trigger compile-time warnings
-dontwarn io.netty.internal.tcnative.**
-dontwarn java.lang.management.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.eclipse.jetty.npn.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**

# 7. Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# 8. Coil Image Loading
-dontwarn coil.**
-keep class coil.** { *; }

# 9. App Entry Points and Lifecycle Observers
-keep class com.vaydelux.bousygram.app.BousygramApp { *; }
-keep class com.vaydelux.bousygram.app.MainActivity { *; }

# 10. Baseline Profile & ProfileInstaller
-dontwarn androidx.profileinstaller.**
-keep class androidx.profileinstaller.** { *; }

# 11. Serializable Data Models (MediaItem, PlaybackSource, SeriesDetails)
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
