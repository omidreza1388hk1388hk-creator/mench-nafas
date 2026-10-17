# ==========================================================
# MENCH release ProGuard/R8 rules
# ==========================================================
# proguard-rules.pro was empty before this, while isMinifyEnabled=true —
# meaning every release build was relying on R8's built-in Android/Kotlin
# rules alone, with nothing project-specific for the reflection-heavy
# libraries below. Each block names the library and why it needs keeping.

# --- Moshi (moshi-kotlin + converter-moshi) ---------------------------
# Moshi's codegen adapters are looked up by reflection on the model
# class name (<ModelName>JsonAdapter); R8 renaming/removing either side
# breaks this at runtime with a silent-looking crash on first parse.
-keepattributes *Annotation*
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class **JsonAdapter { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
# Belt-and-suspenders on top of @JsonClass class * above: every class
# actually annotated @JsonClass is already kept, this just also covers
# any wire-format class under these packages that isn't annotated
# directly (e.g. a plain data class referenced only via Moshi's Kotlin
# reflection fallback).
# Retrofit request/response models (ChatApi.kt etc.) are plain data classes
# parsed by KotlinJsonAdapterFactory (reflection, see NetworkModule), NOT
# @JsonClass codegen — so their constructors/fields/Kotlin metadata must
# survive R8 or every API response fails to parse in release builds.
-keep class com.omidgame.mench.core.network.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class * { @kotlin.Metadata *; }
-keep class com.omidgame.mench.core.network.realtime.** { *; }
-keep class com.omidgame.mench.core.work.** { *; }
-keep class com.omidgame.mench.feature.*.data.** { *; }
-keepclassmembers class com.omidgame.mench.** implements com.squareup.moshi.JsonAdapter { *; }

# --- Retrofit / OkHttp --------------------------------------------------
-keepattributes Signature, Exceptions
-keep interface com.omidgame.mench.core.network.**Api { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.Response { *; }

# --- Room ---------------------------------------------------------------
# Room's generated *_Impl classes and @Entity/@Dao classes are referenced
# by generated code via reflection at annotation-processing time, not by
# name from app code, so R8 can otherwise strip constructors it thinks
# are unused.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }
-keep interface * extends androidx.room.Dao

# --- Stream WebRTC (io.getstream:stream-webrtc-android, org.webrtc.*) --
# The native/JNI layer calls into org.webrtc.* by class/method name from
# C++; keeping only what's reachable from Kotlin isn't enough because R8
# can't see the JNI call sites.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# --- Hilt / Dagger --------------------------------------------------------
# The Hilt Gradle plugin already injects most of what's needed; this is
# belt-and-suspenders for generated component/entry-point classes.
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager
-keepclasseswithmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <methods>;
}

# --- Firebase Messaging ---------------------------------------------------
-keep class com.google.firebase.messaging.** { *; }
-dontwarn com.google.firebase.**

# --- Kotlin coroutines / kotlinx ------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlin.Metadata { *; }

# --- General: keep line numbers for crash reports, drop source file name -
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# --- Home-screen widget / receivers are referenced from the manifest by name
-keep class com.omidgame.mench.core.notifications.** { *; }
