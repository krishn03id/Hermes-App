# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class id.krishn03.hermes.**$$serializer { *; }
-keepclassmembers class id.krishn03.hermes.** {
    *** Companion;
}
-keepclasseswithmembers class id.krishn03.hermes.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
