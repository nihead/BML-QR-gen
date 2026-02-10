# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep data classes for Retrofit/Gson
-keep class com.paymv.posterminal.data.model.** { *; }
-keepclassmembers class com.paymv.posterminal.data.model.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
