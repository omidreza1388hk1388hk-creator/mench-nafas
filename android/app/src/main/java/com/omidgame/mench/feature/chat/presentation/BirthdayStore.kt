package com.omidgame.mench.feature.chat.presentation

import android.content.Context
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.omidgame.mench.R
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Phase 3 — birthdays, kept on this phone only (month and day; the year is never stored). On a birthday the chat plays a
 * balloons effect the first time it is opened that day, and the couple profile counts down to your partner's birthday.
 */
object BirthdayStore {
    private const val PREFS = "birthdays"
    const val MINE = "mine"
    const val PARTNER = "partner"

    fun get(context: Context, who: String): MonthDay? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(who, null)
            ?.let { runCatching { MonthDay.parse("--$it") }.getOrNull() }

    fun set(context: Context, who: String, birthday: MonthDay?) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (birthday == null) remove(who) else putString(who, "%02d-%02d".format(birthday.monthValue, birthday.dayOfMonth))
        }.apply()
    }

    /** The balloons effect when today is your or your partner's birthday, else null. */
    fun effectToday(context: Context, today: LocalDate): MessageEffect? =
        listOf(MINE, PARTNER).mapNotNull { get(context, it) }.firstOrNull { daysUntilBirthday(it, today) == 0L }
            ?.let { MessageEffect.BALLOONS }
}

/** Days from [today] to the next occurrence of [birthday] (0 = today). A 29 February birthday falls on 28 February in other years. */
internal fun daysUntilBirthday(birthday: MonthDay, today: LocalDate): Long {
    var next = birthday.atYear(today.year)
    if (next.isBefore(today)) next = birthday.atYear(today.year + 1)
    return ChronoUnit.DAYS.between(today, next)
}

/** The couple-profile tile: tap to set your partner's birthday; then it counts down. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayTile() {
    val context = LocalContext.current
    var birthday by remember { mutableStateOf(BirthdayStore.get(context, BirthdayStore.PARTNER)) }
    var picking by remember { mutableStateOf(false) }
    val days = birthday?.let { daysUntilBirthday(it, LocalDate.now()) }
    val text = when {
        days == null -> stringResource(R.string.birthday_set)
        days == 0L -> stringResource(R.string.birthday_today)
        else -> stringResource(R.string.birthday_in, days)
    }
    ProfileInfoTile("🎂", text, onClick = { picking = true })
    if (picking) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        val md = MonthDay.of(date.month, date.dayOfMonth)
                        BirthdayStore.set(context, BirthdayStore.PARTNER, md)
                        birthday = md
                    }
                    picking = false
                }) { Text(stringResource(R.string.us_confirm)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.us_cancel)) } },
        ) { DatePicker(state = state) }
    }
}
