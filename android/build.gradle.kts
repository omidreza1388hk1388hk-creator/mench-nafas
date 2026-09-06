// Top-level build file. Per-module plugin versions are declared here once
// (via the plugins {} block with apply false) and applied in :app, so
// every module in future phases (feature modules etc.) stays on one
// consistent toolchain version.
plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    // Kotlin 2.0+ moved the Compose compiler out of the old
    // kotlinCompilerExtensionVersion scheme into this dedicated plugin,
    // versioned in lockstep with the Kotlin plugin itself. Must match
    // org.jetbrains.kotlin.android's version exactly.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
    id("com.google.devtools.ksp") version "2.0.20-1.0.25" apply false
    // Phase 6 (push notifications). Declared with apply false here and
    // applied conditionally in app/build.gradle.kts ONLY if
    // app/google-services.json exists — see that file's comment. This
    // keeps the project buildable without real Firebase credentials
    // (master-prompt principle #87: "the generated project must still
    // build without real production credentials where technically
    // possible"), the same way the backend falls back to
    // DevNotificationProvider rather than requiring Firebase env vars.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
