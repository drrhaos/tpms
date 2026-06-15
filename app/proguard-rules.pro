-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.tpms.app.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# USB / serialization used in settings export
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
