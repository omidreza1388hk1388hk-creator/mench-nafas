package com.omidgame.mench.core.ui.components

import android.icu.util.Calendar as IcuCalendar
import android.icu.util.ULocale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omidgame.mench.R
import com.omidgame.mench.core.ui.theme.MenchDigits
import java.util.Calendar
import java.util.GregorianCalendar

private val JALALI_MONTHS = arrayOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
)

/** Jalali (year, month 0..11, day) for an instant. */
private fun persianCalendar(): IcuCalendar = IcuCalendar.getInstance(ULocale("fa_IR@calendar=persian"))

private fun toJalali(millis: Long): Triple<Int, Int, Int> {
    val c = persianCalendar().apply { timeInMillis = millis }
    return Triple(c.get(IcuCalendar.YEAR), c.get(IcuCalendar.MONTH), c.get(IcuCalendar.DAY_OF_MONTH))
}

/** Days in a Jalali month: 31 for the first six, 30 for the next five, 29 or 30 for Esfand (leap years). */
private fun daysInJalaliMonth(year: Int, month: Int): Int {
    val c = persianCalendar()
    c.clear()
    c.set(year, month, 1)
    return c.getActualMaximum(IcuCalendar.DAY_OF_MONTH)
}

private fun jalaliToMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
    val c = persianCalendar()
    c.clear()
    c.set(year, month, day, hour, minute, 0)
    return c.timeInMillis
}

/**
 * Branded Jalali date (+ optional time) picker used by reminders and events. Returns an epoch-millis instant
 * in the device time zone. Steppers instead of a calendar grid: reliable with one hand, no experimental APIs.
 * A Gregorian preview underneath keeps the two calendars visible together.
 */
@Composable
fun JalaliDateTimePickerDialog(
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit,
    withTime: Boolean = true,
) {
    val start = remember { initialMillis ?: (System.currentTimeMillis() + 3_600_000L) }
    val startJalali = remember { toJalali(start) }
    val startCal = remember { Calendar.getInstance().apply { timeInMillis = start } }
    var year by remember { mutableIntStateOf(startJalali.first) }
    var month by remember { mutableIntStateOf(startJalali.second) }
    var day by remember { mutableIntStateOf(startJalali.third) }
    var hour by remember { mutableIntStateOf(startCal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(startCal.get(Calendar.MINUTE)) }

    // Clamp the day whenever the year/month change (e.g. 31 Farvardin -> Mehr is fine, -> Esfand 29/30 is not).
    val maxDay = daysInJalaliMonth(year, month)
    if (day > maxDay) day = maxDay
    val picked = jalaliToMillis(year, month, day, if (withTime) hour else 0, if (withTime) minute else 0)

    MenchDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.jalali_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Stepper(
                        label = MenchDigits.toPersian(day.toString()),
                        onUp = { day = if (day >= maxDay) 1 else day + 1 },
                        onDown = { day = if (day <= 1) maxDay else day - 1 },
                    )
                    Stepper(
                        label = JALALI_MONTHS[month.coerceIn(0, 11)],
                        onUp = { month = (month + 1) % 12 },
                        onDown = { month = (month + 11) % 12 },
                        wide = true,
                    )
                    Stepper(
                        label = MenchDigits.toPersian(year.toString()),
                        onUp = { year += 1 },
                        onDown = { year -= 1 },
                    )
                }
                if (withTime) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Stepper(
                            label = MenchDigits.toPersian("%02d".format(hour)),
                            onUp = { hour = (hour + 1) % 24 },
                            onDown = { hour = (hour + 23) % 24 },
                        )
                        Text(":", style = MaterialTheme.typography.headlineSmall)
                        Stepper(
                            label = MenchDigits.toPersian("%02d".format(minute)),
                            onUp = { minute = (minute + 5) % 60 },
                            onDown = { minute = (minute + 55) % 60 },
                        )
                    }
                }
                val gregorian = GregorianCalendar().apply { timeInMillis = picked }
                Text(
                    text = MenchDigits.toPersian(
                        "%04d-%02d-%02d".format(
                            gregorian.get(Calendar.YEAR),
                            gregorian.get(Calendar.MONTH) + 1,
                            gregorian.get(Calendar.DAY_OF_MONTH),
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPicked(picked) }) { Text(stringResource(R.string.jalali_picker_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Stepper(label: String, onUp: () -> Unit, onDown: () -> Unit, wide: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onUp) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.jalali_picker_increase))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            modifier = Modifier.widthIn(min = if (wide) 72.dp else 48.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onDown) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.jalali_picker_decrease))
        }
    }
}
