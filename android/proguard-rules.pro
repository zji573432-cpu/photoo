# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.voicelike.app.AppStats { *; }
-keep class com.voicelike.app.MediaItem { *; }
-keep class com.voicelike.app.LocalizedStrings { *; }
-keep class com.voicelike.app.AppLanguage { *; }

# Gson & Reflection
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Kotlin
-keep class kotlin.Metadata { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.CoroutineExceptionHandler {
    <init>(...);
}

# Compose (Generally handled by R8, but good to be safe)
# -keep class androidx.compose.** { *; }

# ExoPlayer (Reduce stripping of required components if issues arise, but start strict)
# -keep class androidx.media3.** { *; }

# Prevent R8 from stripping the generic type information from fields/methods
# which Gson relies on for TypeToken
-keepattributes EnclosingMethod
-keepattributes InnerClasses
