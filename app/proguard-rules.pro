# ProGuard rules for Room & Compose
-dontwarn androidx.room.**
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Database *;
    @androidx.room.Entity *;
}
