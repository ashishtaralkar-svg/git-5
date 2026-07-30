# Keep Hilt generated components
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Retrofit / Gson DTOs
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.hdfc.docupload.data.remote.dto.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Room
-keep class * extends androidx.room.RoomDatabase { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
