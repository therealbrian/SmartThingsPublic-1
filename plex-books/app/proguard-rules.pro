-keep class com.plexbooks.data.api.model.** { *; }
-keepclassmembers class com.plexbooks.data.api.model.** { *; }
-keep class com.squareup.moshi.** { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-dontwarn okhttp3.**
-dontwarn retrofit2.**
