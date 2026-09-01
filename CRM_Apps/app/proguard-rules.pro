# Retrofit / Gson / SignalR models must survive obfuscation.
-keep class com.zynexbd.crmsolution.models.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
