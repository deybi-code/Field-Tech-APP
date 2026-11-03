# Reglas de ProGuard/R8 para Google GenAI SDK y Kotlinx Serialization
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class * {
    @**.Serializable <fields>;
}
-keepnames @**.Serializable class *
-keepclassmembers class * {
    @**.SerialName <fields>;
}
-keep,includedescriptorclasses class kotlinx.serialization.** { *; }
