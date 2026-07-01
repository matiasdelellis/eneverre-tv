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

# Workaround for R8 9.1.x IndexOutOfBoundsException crash in the IR optimizer
# (ir.optimize pass). R8 ignores most fine-grained -optimizations filters, so we
# disable the optimization stage entirely; shrinking and obfuscation still run.
-dontoptimize

# Keep EneverreTv
-keep class ar.com.delellis.eneverretv.** { *; }

# rtsp-client-android
-keep class com.alexvas.rtsp.** { *; }

# Evita problemas con reflection
-keepattributes *Annotation*
-keepattributes Signature

# Mantener Activities / Services
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service

# Kotlin (si usás)
-keep class kotlin.Metadata { *; }

# Evitar warnings molestos
-dontwarn com.alexvas.rtsp.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

