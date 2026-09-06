plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// Phase 6: applied imperatively (rather than in the plugins {} block
// above, which cannot be conditional) ONLY when a real
// google-services.json is present. Without it, the Firebase Messaging
// dependency below still compiles and links fine — it just has no
// default FirebaseApp to attach to at runtime, which
// PushTokenRegistrar.fetchTokenOrNull() and MenchFirebaseMessagingService
// are both written to detect and degrade gracefully from (see their doc
// comments). This mirrors the backend's NotificationsModule falling back
// to DevNotificationProvider when Firebase env vars are absent — same
// principle, applied at the Gradle level instead of the NestJS DI level.
// See docs/ENVIRONMENT.md for how to obtain a real google-services.json.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.omidgame.mench"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.omidgame.mench"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.7.0-phase6-merged"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Base URL is injected per build, resolved at Gradle configuration
        // time and never hardcoded as a production value in source (spec
        // 5/80). The buildConfigField VALUE argument must itself be a
        // string containing valid Java-source-literal text (i.e. wrapped
        // in escaped quotes), which is why the outer \" \" wrap the
        // interpolated value below.
        val apiBaseUrl = project.findProperty("MENCH_API_BASE_URL") as String?
            ?: "http://10.0.2.2:3000/api/v1/"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // No composeOptions { kotlinCompilerExtensionVersion = ... } here:
    // that mechanism only applies to Kotlin 1.9.x + separately-versioned
    // Compose compiler 1.5.x. With Kotlin 2.0.20 (declared above), the
    // org.jetbrains.kotlin.plugin.compose plugin auto-selects the matching
    // Compose compiler — setting kotlinCompilerExtensionVersion alongside
    // Kotlin 2.0+ is both unnecessary and was a version-mismatch bug in
    // the original Phase 1 build file.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Room (via KSP) needs an explicit schema export location since
// AppDatabase declares exportSchema = true — without this, Room silently
// skips writing the schema JSON and future Migration tests would have
// nothing to validate against.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    // ProcessLifecycleOwner — drives AppLockCoordinator's foreground/
    // background detection at the process level, independent of any one
    // Activity's own lifecycle (spec section 32: auto-lock on background).
    implementation("androidx.lifecycle:lifecycle-process:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    // MainActivity is a FragmentActivity (not a plain ComponentActivity)
    // solely so BiometricPrompt has a valid host — BiometricPrompt's
    // constructor requires a FragmentActivity/Fragment, there is no
    // ComponentActivity overload.
    implementation("androidx.fragment:fragment-ktx:1.8.4")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Dependency injection
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Durable background work (Outbox retry — see OutboxSyncWorker)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Local persistence
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Secure local token storage (Android Keystore-backed)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    // Generates real adapter classes at compile time for every
    // @JsonClass(generateAdapter = true) data class (RealtimeEvents,
    // OutboxPayloads, ChatApi's response types). Without this, those
    // annotations are inert and Moshi silently falls back to the slower
    // KotlinJsonAdapterFactory reflection path from moshi-kotlin above —
    // not a crash, but not what the annotations actually claim either.
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Image loading for attachment thumbnails/full images — uses its own
    // ImageLoader (see core/di/ImageLoaderModule.kt) built on the
    // @AuthenticatedClient OkHttpClient, since attachment content/
    // thumbnail endpoints require a bearer token like every other API
    // call — a plain unauthenticated image loader would just get 401s.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // AttachFile/InsertDriveFile (used in the chat composer/file bubble)
    // are NOT part of the small curated icon set bundled by default with
    // material3 (material-icons-core) — only Add/Send/ArrowBack-tier
    // common icons are. Using anything outside that common set requires
    // this extended artifact explicitly, or the reference fails to
    // resolve at compile time.
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.13")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Real WebRTC (spec section 29 — "do not invent networking protocols").
    // Google stopped publishing org.webrtc:google-webrtc to Maven Central/
    // JCenter years ago; stream-webrtc-android is the actively maintained
    // republish of the same upstream libwebrtc native build, under the
    // same org.webrtc.* package names/API surface (PeerConnectionFactory,
    // PeerConnection, MediaStream, SurfaceViewRenderer, ...) — so
    // core/webrtc/WebRtcClient.kt is written against the standard WebRTC
    // Android API, not a proprietary wrapper.
    implementation("io.getstream:stream-webrtc-android:1.1.1")

    // .await() extension on a Firebase Task<T> (used by PushTokenRegistrar
    // to fetch the current FCM token) — this is a separate artifact from
    // kotlinx-coroutines-android above, not bundled with it.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Push notifications (Phase 6). The BoM pins a compatible messaging
    // version; no google-services.json is required for this to COMPILE
    // (see the conditional plugin application above) — only for it to
    // actually deliver a push at runtime.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging-ktx")
}
