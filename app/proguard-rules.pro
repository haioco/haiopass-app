-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keep public class * extends java.lang.Exception

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep API service interface methods with their generic signatures
# Kotlin suspend functions need explicit signature preservation
-keep,allowobfuscation,allowshrinking class com.haio.bypass.network.api.HaioApiService {
    <methods>;
}

# Kotlin reflect + generic signatures — needed by Retrofit type resolution
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.full.** { *; }
-keep class kotlin.reflect.jvm.** { *; }
-keep class kotlin.coroutines.Continuation; }
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Keep API models
-keep class com.haio.bypass.network.api.** { *; }
-keepclassmembers class com.haio.bypass.network.api.** { *; }

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.haio.bypass.**$$serializer { *; }
-keepclassmembers class com.haio.bypass.** {
    *** Companion;
}
-keepclasseswithmembers class com.haio.bypass.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# Keep raw resources
-keep class com.haio.bypass.R$raw { *; }

# Kotlin reflection — needed for Retrofit type tokens
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }