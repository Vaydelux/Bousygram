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
# TDLib C++ native library makes reflective JNI calls into Java classes, fields, constructors, and callbacks.
# R8 full-mode minification must preserve all field names, constructors, and native methods.
-keep class org.drinkless.tdlib.Client {
    native <methods>;
    public <methods>;
    private <methods>;
}
-keep class org.drinkless.tdlib.Client$* {
    *;
}
-keep class org.drinkless.tdlib.TdApi {
    *;
}
-keep class org.drinkless.tdlib.TdApi$* {
    <fields>;
    public <init>(...);
    public <methods>;
}
-keepclassmembers class org.drinkless.tdlib.TdApi$* {
    <fields>;
    public <init>(...);
}
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
# Silence non-Android Netty native transports and optional logging
-dontwarn io.netty.channel.epoll.**
-dontwarn io.netty.channel.kqueue.**
-dontwarn io.netty.channel.unix.**
-dontwarn io.netty.handler.ssl.**
-dontwarn io.netty.internal.tcnative.**
-dontwarn io.netty.util.internal.logging.**
-dontwarn io.netty.util.internal.shaded.org.jctools.**
-dontwarn java.lang.management.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.eclipse.jetty.npn.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn io.ktor.**

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
-keepclassmembers class io.netty.util.concurrent.FastThreadLocalRunnable {
    *;
}
-keepclassmembers class io.netty.channel.ChannelHandlerAdapter {
    *;
}
-keepclassmembers class io.netty.channel.ChannelInitializer {
    *;
}
-keep class io.netty.channel.socket.nio.NioSocketChannel {
    public <init>();
}
-keep class io.netty.channel.socket.nio.NioServerSocketChannel {
    public <init>();
}

# Ktor Netty Server Engine reflection and ServiceLoader discovery
-keep class io.ktor.server.netty.** {
    public <init>(...);
    *;
}
-keep class * implements io.ktor.server.engine.ApplicationEngineFactory { *; }
-keep class * implements io.ktor.server.engine.ApplicationEngine { *; }
-keep class * implements io.ktor.server.engine.EnginePipeline { *; }
-keepnames class * implements kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class * implements kotlinx.coroutines.CoroutineExceptionHandler

# 7. Media3 / ExoPlayer Production & Reflection Rules
-dontwarn androidx.media3.**

# Keep renderers and extractors factories loaded dynamically via reflection
-keep class androidx.media3.exoplayer.DefaultRenderersFactory {
    public <init>(...);
}
-keep class androidx.media3.exoplayer.RenderersFactory { *; }
-keep class androidx.media3.extractor.DefaultExtractorsFactory {
    public <init>(...);
}
-keep class androidx.media3.extractor.ExtractorsFactory { *; }

# MediaCodec discovery and adapter reflection
-keep class androidx.media3.exoplayer.mediacodec.MediaCodecSelector { *; }
-keep class androidx.media3.exoplayer.mediacodec.MediaCodecAdapter$Factory { *; }
-keep class androidx.media3.exoplayer.mediacodec.SynchronousMediaCodecAdapter$Factory {
    public <init>(...);
}
-keep class androidx.media3.exoplayer.mediacodec.AsynchronousMediaCodecAdapter$Factory {
    public <init>(...);
}

# Keep all Media3 component constructors
-keep class androidx.media3.exoplayer.** {
    public <init>(...);
}
-keep class androidx.media3.extractor.** {
    public <init>(...);
}
-keep class androidx.media3.datasource.** {
    public <init>(...);
}
-keep class androidx.media3.ui.** {
    public <init>(...);
}
-keep class androidx.media3.session.** {
    public <init>(...);
}

# Preserve Media3 common data structures accessed reflectively
-keepclassmembers class androidx.media3.common.Format { *; }
-keepclassmembers class androidx.media3.common.MediaItem { *; }
-keepclassmembers class androidx.media3.common.MediaMetadata { *; }
-keepclassmembers class androidx.media3.common.TrackGroup { *; }
-keepclassmembers class androidx.media3.common.Tracks { *; }
-keepclassmembers class androidx.media3.common.PlaybackParameters { *; }
-keepclassmembers class androidx.media3.common.Timeline { *; }

# Audio and video processors / renderers
-keep class androidx.media3.exoplayer.audio.** {
    public <init>(...);
}
-keep class androidx.media3.exoplayer.video.** {
    public <init>(...);
}
-keep class androidx.media3.exoplayer.text.** {
    public <init>(...);
}

# 8. Coil Image Loading
-dontwarn coil.**
-keep class * implements coil.ComponentRegistry$Builder { *; }
-keep class * implements coil.decode.Decoder$Factory { *; }
-keep class * implements coil.fetch.Fetcher$Factory { *; }
-keepclassmembers class coil.ImageLoader {
    *;
}

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
