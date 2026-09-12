# ProGuard rules for Jarvis Assistant
-keep class com.jarvis.assistant.data.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
