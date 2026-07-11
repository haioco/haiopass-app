-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# kotlinx.serialization
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
-keep class okio.** { *; }

# Retrofit + Gson
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepclassmembers class com.haio.bypass.network.api.ApiModels$** { *; }
-keep class com.google.gson.** { *; }

# Keep raw resources (accessed via getIdentifier)
-keep class com.haio.bypass.R$raw { *; }
