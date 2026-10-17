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
        versionName = "4.0.0-all-phases"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Base URL is injected per build, resolved at Gradle configuration
        // time and never hardcoded as a production value in source (spec
        // 5/80). The buildConfigField VALUE argument must itself be a
        // string containing valid Java-source-literal text (i.e. wrapped
        // in escaped quotes), which is why the outer \" \" wrap the
        // interpolated value below.
        // Production is the safe default so a release/debug APK built locally
        // cannot silently point at the Android-emulator loopback address.
        // Local development can still override this with:
        // -PMENCH_API_BASE_URL="http://10.0.2.2:3000/api/v1/"
        val configuredApiBaseUrl = project.findProperty("MENCH_API_BASE_URL") as String?
        val apiBaseUrl = (configuredApiBaseUrl
            ?: "https://mench-production.up.railway.app/api/v1/")
            .trim()
            .let { if (it.endsWith("/")) it else "$it/" }
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    // Release signing, read from gradle.properties / -P args / env vars so
    // no keystore path or password is ever committed. Generate a keystore
    // once with:
    //   keytool -genkeypair -v -keystore mench-release.keystore \
    //     -alias mench -keyalg RSA -keysize 2048 -validity 10000
    // then either add to ~/.gradle/gradle.properties (not the project's,
    // so it never ends up in git):
    //   MENCH_RELEASE_STORE_FILE=/full/path/to/mench-release.keystore
    //   MENCH_RELEASE_STORE_PASSWORD=...
    //   MENCH_RELEASE_KEY_ALIAS=mench
    //   MENCH_RELEASE_KEY_PASSWORD=...
    // or export the same names as environment variables (handy in Termux).
    // If none are set, the release build type silently falls back to the
    // debug signing config so `assembleRelease` still produces an
    // installable (self-signed-debug) APK instead of failing outright —
    // but that APK cannot receive updates signed with a real key later,
    // so treat the fallback as dev-only.
    val releaseStoreFile = (project.findProperty("MENCH_RELEASE_STORE_FILE") as String?)
        ?: System.getenv("MENCH_RELEASE_STORE_FILE")
    val releaseStorePassword = (project.findProperty("MENCH_RELEASE_STORE_PASSWORD") as String?)
        ?: System.getenv("MENCH_RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = (project.findProperty("MENCH_RELEASE_KEY_ALIAS") as String?)
        ?: System.getenv("MENCH_RELEASE_KEY_ALIAS")
    val releaseKeyPassword = (project.findProperty("MENCH_RELEASE_KEY_PASSWORD") as String?)
        ?: System.getenv("MENCH_RELEASE_KEY_PASSWORD")
    val hasRealReleaseSigning = !releaseStoreFile.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        if (hasRealReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
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
            signingConfig = if (hasRealReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        // Phase 1B completion: release-like build the :benchmark module measures and records profiles against.
        // Signed with the debug key so it installs without the release keystore.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
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
        // Compose marks parts of Material3 / Foundation / shared-element
        // transitions as experimental. Opting in once at the compiler level is
        // cleaner than scattering @OptIn across ~40 files, and these APIs are
        // stable in practice on the pinned BOM (2024.09.03).
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
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
    // Phase 2A: in-app round video recording (circular preview, tap to record).
    val cameraXVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-video:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")
    // Phase 1A: system splash (Android 12+ native, compat below) handing off to AnimatedSplash.
    implementation("androidx.core:core-splashscreen:1.0.1")
    // Phase 1B: EXIF orientation for the image compressor.
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // Phase 1C: QR generate (ZXing core) + scan (CameraX preview/analysis, no Google Play Services).
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    // Phase 1B: installs src/main/baseline-prof.txt on devices without Play-delivered profiles.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    // Phase 1C completion: Glance home-screen widgets (couple counter, partner quick messages).
    implementation("androidx.glance:glance-appwidget:1.1.0")
    // Phase 1B completion: video transcoding before upload.
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
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
    // Vazirmatn (Persian/Arabic-first) via the Google Fonts downloadable-
    // fonts provider — see core/ui/theme/Font.kt's doc comment for why
    // this is fetched at runtime rather than bundled as .ttf assets.
    implementation("androidx.compose.ui:ui-text-google-fonts")
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
    // Phase 2A: animated GIF decoding for the GIF picker / GIF messages (same version as coil-compose).
    implementation("io.coil-kt:coil-gif:2.7.0")

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
