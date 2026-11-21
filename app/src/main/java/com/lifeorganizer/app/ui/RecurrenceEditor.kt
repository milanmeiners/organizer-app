package com.lifeorganizer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifeorganizer.app.domain.Freq
import com.lifeorganizer.app.domain.RRule
import com.lifeorganizer.app.domain.Recurrence
import com.lifeorganizer.app.domain.WeekdayNum
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Editor for an iCalendar RRULE. [rrule] null means "does not repeat".
 * [start] is the first occurrence (used for defaults and the description).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurrenceEditor(rrule: String?, start: LocalDate, today: LocalDate, onChange: (String?) -> Unit) {
    val rule = RRule.parse(rrule)
    var advanced by remember { mutableStateOf(false) }
    var rawText by remember { mutableStateOf(rrule ?: "") }

    SwitchRow("Repeat", rule != null, onChange = { on ->
        onChange(if (on) RRule(Freq.WEEKLY, byDay = listOf(WeekdayNum(0, start.dayOfWeek))).toRRuleString() else null)
    }, sub = rule?.let { Recurrence.describe(it, start) })

    if (rule == null) return
    fun update(r: RRule) = onChange(r.toRRuleString())

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((f, label) in listOf(Freq.DAILY to "Daily", Freq.WEEKLY to "Weekly", Freq.MONTHLY to "Monthly", Freq.YEARLY to "Yearly")) {
                MonoChip(label, rule.freq == f, {
                    val r = when (f) {
                        Freq.WEEKLY -> RRule(f, rule.interval, byDay = listOf(WeekdayNum(0, start.dayOfWeek)), count = rule.count, until = rule.until)
                        Freq.MONTHLY -> RRule(f, rule.interval, byMonthDay = listOf(start.dayOfMonth), count = rule.count, until = rule.until)
                        else -> RRule(f, rule.interval, count = rule.count, until = rule.until)
                    }
                    update(r)
                })
            }
        }

        // Interval
        val unit = when (rule.freq) { Freq.DAILY -> "day"; Freq.WEEKLY -> "week"; Freq.MONTHLY -> "month"; Freq.YEARLY -> "year" }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Every", modifier = Modifier.width(64.dp))
            Stepper(rule.interval, 1, 99) { update(rule.copy(interval = it)) }
            Text(if (rule.interval == 1) unit else unit + "s")
        }

        when (rule.freq) {
            Freq.WEEKLY -> {
                Text("On", style = SectionLabel, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                val selected = rule.byDay.map { it.day }.toSet().ifEmpty { setOf(start.dayOfWeek) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in DayOfWeek.entries) {
                        val label = RRule.dayCode(d).lowercase().replaceFirstChar { it.uppercase() }
                        MonoChip(label, d in selected, {
                            val next = if (d in selected) selected - d else selected + d
                            if (next.isNotEmpty()) update(rule.copy(byDay = next.sortedBy { it.value }.map { WeekdayNum(0, it) }))
                        })
                    }
                }
                Row(Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = { update(rule.copy(byDay = DayOfWeek.entries.take(5).map { WeekdayNum(0, it) })) }) { Text("Weekdays") }
                    TextButton(onClick = { update(rule.copy(byDay = DayOfWeek.entries.map { WeekdayNum(0, it) })) }) { Text("All days") }
                }
            }
            Freq.MONTHLY -> {
                Text("On", style = SectionLabel, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                val nth = (start.dayOfMonth - 1) / 7 + 1
                val isLastWeek = start.plusWeeks(1).month != start.month
                val dayName = start.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MonoChip("Day ${start.dayOfMonth}", rule.byDay.isEmpty() && rule.byMonthDay != listOf(-1), {
                        update(rule.copy(byDay = emptyList(), byMonthDay = listOf(start.dayOfMonth)))
                    })
                    if (nth <= 4) {
                        MonoChip("${Recurrence.ordinalWord(nth).replaceFirstChar { it.uppercase() }} $dayName",
                            rule.byDay == listOf(WeekdayNum(nth, start.dayOfWeek)), {
                                update(rule.copy(byMonthDay = emptyList(), byDay = listOf(WeekdayNum(nth, start.dayOfWeek))))
                            })
                    }
                    if (isLastWeek) {
                        MonoChip("Last $dayName", rule.byDay == listOf(WeekdayNum(-1, start.dayOfWeek)), {
                            update(rule.copy(byMonthDay = emptyList(), byDay = listOf(WeekdayNum(-1, start.dayOfWeek))))
                        })
                    }
                    MonoChip("Last day", rule.byMonthDay == listOf(-1) && rule.byDay.isEmpty(), {
                        update(rule.copy(byDay = emptyList(), byMonthDay = listOf(-1)))
                    })
                }
            }
            else -> Unit
        }

        // End
        Text("Ends", style = SectionLabel, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MonoChip("Never", rule.count == null && rule.until == null, { update(rule.copy(count = null, until = null)) })
            MonoChip("After…", rule.count != null, { update(rule.copy(count = rule.count ?: 10, until = null)) })
            MonoChip("On date…", rule.until != null, { update(rule.copy(count = null, until = rule.until ?: start.plusMonths(3))) })
        }
        if (rule.count != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text("After", modifier = Modifier.width(64.dp))
                Stepper(rule.count, 1, 999) { update(rule.copy(count = it)) }
                Text("occurrences")
            }
        }
    }
    if (rule.until != null) {
        DateFieldRow("End date", rule.until, today, onChange = { d -> update(rule.copy(until = d ?: start)) }, clearable = false)
    }

    // Advanced: raw RRULE for fully custom patterns
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        TextButton(onClick = { if (!advanced) rawText = rrule ?: ""; advanced = !advanced }, modifier = Modifier.padding(top = 4.dp)) {
            Text(if (advanced) "Hide custom rule" else "Custom rule (iCalendar RRULE)…")
        }
        if (advanced) {
            val parsed = RRule.parse(rawText)
            MonoTextField(
                value = rawText,
                onValueChange = { rawText = it; RRule.parse(it)?.let { r -> onChange(r.toRRuleString()) } },
                label = "RRULE",
                isError = parsed == null,
                supportingText = if (parsed == null) "Invalid rule. Example: FREQ=MONTHLY;BYDAY=2TU;COUNT=6" else Recurrence.describe(parsed, start),
            )
        }
    }
}

@Composable
fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (value > min) onChange(value - 1) }) { Icon(Icons.Filled.Remove, "Less") }
        Text(value.toString(), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { if (value < max) onChange(value + 1) }) { Icon(Icons.Filled.Add, "More") }
    }
}
