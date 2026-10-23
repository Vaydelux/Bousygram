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
-keepattributes SourceFile,LineNumberTable,*Annotation*

# TDLib Java bindings are consumed via native/bridge calls and should not be obfuscated away.
-keep class org.drinkless.tdlib.** { *; }

# libmpv bindings and native JNI bridge
-keep class dev.jdtech.mpv.** { *; }
-dontwarn dev.jdtech.mpv.**

# Preserve app entry points and components.
-keep class com.vaydelux.bousygram.app.BousygramApp { *; }
-keep class com.vaydelux.bousygram.app.MainActivity { *; }

# Ktor/Netty references optional desktop/server logging and native SSL integrations.
# Bousygram's proxy binds only to 127.0.0.1 on Android and does not package those modules.
-dontwarn io.netty.internal.tcnative.**
-dontwarn java.lang.management.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.eclipse.jetty.npn.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
