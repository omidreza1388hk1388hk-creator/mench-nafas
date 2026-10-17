package com.omidgame.mench.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val TARGET_PACKAGE = "com.omidgame.mench"

/** Waits for the conversation list (the first scrollable on screen after sign-in). Returns false when the device is not signed in. */
fun MacrobenchmarkScope.waitForConversationList(timeoutMs: Long = 8_000): Boolean =
    device.wait(Until.hasObject(By.scrollable(true)), timeoutMs)

/** Fling the first scrollable down and back up, twice: the scroll-jank scenario of the budget (60 fps). */
fun MacrobenchmarkScope.flingConversationList() {
    val list = device.findObject(By.scrollable(true)) ?: return
    list.setGestureMargin(device.displayWidth / 5)
    repeat(2) {
        list.fling(Direction.DOWN)
        device.waitForIdle()
        list.fling(Direction.UP)
        device.waitForIdle()
    }
}

/**
 * Opens the first row of the conversation list by tapping its position (rows carry no test tags yet),
 * waits for the chat to settle, scrolls the history, then goes back.
 */
fun MacrobenchmarkScope.openFirstChatAndScroll() {
    device.click(device.displayWidth / 2, (device.displayHeight * 0.22f).toInt())
    device.waitForIdle(1_500)
    device.findObject(By.scrollable(true))?.let { messages ->
        messages.setGestureMargin(device.displayWidth / 5)
        messages.fling(Direction.UP)
        device.waitForIdle()
        messages.fling(Direction.DOWN)
        device.waitForIdle()
    }
    device.pressBack()
    device.waitForIdle()
}
