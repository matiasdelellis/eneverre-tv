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

# Workaround for R8 IndexOutOfBoundsException with AGP 9.x + libvlc/okhttp5
# Disable only the short-method inlining pass (the buggy one), keep the rest
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!method/inlining/short

# Keep EneverreTv
-keep class ar.com.delellis.eneverretv.** { *; }

# VLC
-keep class org.videolan.** { *; }
-keep class org.videolan.libvlc.** { *; }

# Evita problemas con reflection
-keepattributes *Annotation*
-keepattributes Signature

# Mantener Activities / Services
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service

# Kotlin (si usás)
-keep class kotlin.Metadata { *; }

# Evitar warnings molestos
-dontwarn org.videolan.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

