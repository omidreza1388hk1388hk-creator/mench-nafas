package com.omidgame.mench.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the real baseline profile (cold start, chat open, scroll). Run on a signed-in device:
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.omidgame.mench.benchmark.BaselineProfileGenerator
 * then copy the generated `*-baseline-prof.txt` from benchmark/build/outputs/connected_android_test_additional_output/
 * over app/src/main/baseline-prof.txt. The hand-written profile that ships today stays valid until you do.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun generate() = rule.collect(packageName = TARGET_PACKAGE) {
        pressHome()
        startActivityAndWait()
        waitForConversationList()
        flingConversationList()
        openFirstChatAndScroll()
    }
}
