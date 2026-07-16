# Retrofit + OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Gson needs real JSON field names on request/response models.
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class com.asrys.arrowgame.PuzzleSeedsResponse { *; }
-keep class com.asrys.arrowgame.StatsRequest { *; }
-keep class com.asrys.arrowgame.SaveProgressRequest { *; }
-keep class com.asrys.arrowgame.CheckPlayerEmailRequest { *; }
-keep class com.asrys.arrowgame.CheckPlayerEmailResponse { *; }
-keep class com.asrys.arrowgame.CreatePlayerRequest { *; }
-keep class com.asrys.arrowgame.ProgressResponse { *; }
-keep class com.asrys.arrowgame.SuccessResponse { *; }
-keep interface com.asrys.arrowgame.GameApi { *; }
