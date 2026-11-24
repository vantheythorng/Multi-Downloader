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
# --- Multi-Downloader ---

# youtubedl-android: Jackson maps yt-dlp's JSON onto these classes by reflection, and the
# library looks up its own classes by name when unpacking Python / FFmpeg / aria2c.
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

# Jackson (used inside youtubedl-android): keep names so reflection-based (de)serialisation works.
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**

# Apache Commons (used inside youtubedl-android) reference JVM-only classes that Android lacks.
-dontwarn org.apache.commons.**
# Commons Compress unpacks Python/FFmpeg/aria2c and registers its zip extra-field classes by
# reflection; without this R8 merges them and init crashes ("class … is not a concrete class").
-keep class org.apache.commons.compress.archivers.zip.** { *; }
