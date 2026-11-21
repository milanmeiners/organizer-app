package com.lifeorganizer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeorganizer.app.data.Priority
import com.lifeorganizer.app.domain.GoalTree
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// ---------------------------------------------------------------- Formatting

object Fmt {
    private val L = Locale.ENGLISH
    val dayLong: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", L)
    val dayMedium: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", L)
    val dayShort: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", L)
    val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", L)
    val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", L)
    val monthName: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", L)
    val weekdayShort: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", L)
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", L)

    fun date(d: LocalDate?, today: LocalDate = LocalDate.now()): String {
        if (d == null) return "None"
        return when (d) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> if (d.year == today.year) d.format(dayShort) else d.format(dayMedium)
        }
    }

    fun time(t: LocalTime): String = t.format(time)

    fun deadline(d: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, d)
        return when {
            days < -1 -> "Overdue · ${-days} days"
            days == -1L -> "Overdue · 1 day"
            days == 0L -> "Due today"
            days == 1L -> "Due tomorrow"
            days <= 6 -> "Due in $days days"
            else -> "Due " + date(d, today)
        }
    }
}

// ---------------------------------------------------------------- Basic elements

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(Locale.ENGLISH), style = SectionLabel, modifier = Modifier.weight(1f))
        if (action != null) action()
    }
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier, start: Dp = 0.dp) {
    HorizontalDivider(modifier.padding(start = start), thickness = 0.5.dp, color = Mono.G200)
}

@Composable
fun ProgressLine(percent: Int, modifier: Modifier = Modifier, height: Dp = 3.dp, track: Color = Mono.G200) {
    val f = (percent.coerceIn(0, 100)) / 100f
    Box(modifier.height(height).clip(RoundedCornerShape(height)).background(track)) {
        Box(Modifier.fillMaxWidth(f).height(height).background(Mono.Black))
    }
}

/** Square (or round) monochrome checkbox with a generous touch target. */
@Composable
fun MonoCheck(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, round: Boolean = false, dashed: Boolean = false) {
    val shape = if (round) CircleShape else RoundedCornerShape(5.dp)
    Box(
        modifier.size(40.dp).clip(CircleShape).clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(20.dp).clip(shape)
                .background(if (checked) Mono.Black else Mono.White)
                .border(1.5.dp, if (checked) Mono.Black else if (dashed) Mono.G300 else Mono.G500, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Filled.Check, contentDescription = "Done", tint = Mono.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun MonoChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.clip(shape)
            .background(if (selected) Mono.Black else Mono.White)
            .border(1.dp, if (selected) Mono.Black else Mono.G300, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) Mono.White else Mono.Ink, maxLines = 1)
    }
}

@Composable
fun PriorityMark(priority: Int, modifier: Modifier = Modifier) {
    if (priority <= 0) return
    Text("!".repeat(priority), modifier = modifier, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (priority >= 3) Mono.Black else Mono.G600)
}

@Composable
fun MonoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingText: String? = null,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Mono.G400) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Mono.Black,
            unfocusedBorderColor = Mono.G300,
            focusedLabelColor = Mono.Black,
            unfocusedLabelColor = Mono.G500,
            cursorColor = Mono.Black,
            errorBorderColor = Mono.Black,
            errorLabelColor = Mono.Black,
            errorSupportingTextColor = Mono.Black,
        ),
    )
}

/** A row "Label ........ value" that opens a picker, with an optional clear button. */
@Composable
fun FieldRow(label: String, value: String, onClick: () -> Unit, onClear: (() -> Unit)? = null, muted: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 8.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = if (muted) Mono.G400 else Mono.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onClear != null) {
            IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Mono.G500, modifier = Modifier.size(18.dp)) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Mono.White, checkedTrackColor = Mono.Black, checkedBorderColor = Mono.Black,
                uncheckedThumbColor = Mono.G500, uncheckedTrackColor = Mono.White, uncheckedBorderColor = Mono.G400,
            ),
        )
    }
}

@Composable
fun ProgressSlider(value: Int, onChange: (Int) -> Unit, onFinished: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((Math.round(it / 5f) * 5).coerceIn(0, 100)) },
            onValueChangeFinished = onFinished,
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Mono.Black, activeTrackColor = Mono.Black, inactiveTrackColor = Mono.G200,
                activeTickColor = Mono.Black, inactiveTickColor = Mono.G200,
            ),
        )
        Text("$value%", modifier = Modifier.width(52.dp).padding(start = 12.dp), fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PriorityPicker(value: Int, onChange: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (p in listOf(Priority.NONE, Priority.LOW, Priority.MEDIUM, Priority.HIGH)) {
            MonoChip(Priority.label(p), value == p, { onChange(p) })
        }
    }
}

@Composable
fun StrikeText(text: String, done: Boolean, modifier: Modifier = Modifier, maxLines: Int = 2) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = if (done) Mono.G400 else Mono.Ink,
        textDecoration = if (done) TextDecoration.LineThrough else null,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---------------------------------------------------------------- Dialogs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerModal(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

@Composable
fun DateFieldRow(label: String, value: LocalDate?, today: LocalDate, onChange: (LocalDate?) -> Unit, clearable: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    FieldRow(
        label = label,
        value = value?.let { Fmt.date(it, today) } ?: "None",
        onClick = { open = true },
        onClear = if (clearable && value != null) ({ onChange(null) }) else null,
        muted = value == null,
    )
    if (open) DatePickerModal(value, onDismiss = { open = false }, onPick = { onChange(it) })
}

@Composable
fun TimeFieldRow(label: String, value: LocalTime, onChange: (LocalTime) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FieldRow(label, Fmt.time(value), onClick = { open = true })
    if (open) TimePickerModal(value, onDismiss = { open = false }, onPick = onChange)
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Simple list of choices in a dialog. */
@Composable
fun ChoiceDialog(title: String, text: String?, options: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (text != null) Text(text, modifier = Modifier.padding(bottom = 12.dp))
                for ((label, action) in options) {
                    Text(
                        label,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { action(); onDismiss() }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Hierarchical goal selector. [exclude] removes goals (and their subtrees) that may not be chosen. */
@Composable
fun GoalPickerDialog(
    tree: GoalTree,
    title: String,
    selected: Long?,
    exclude: Set<Long> = emptySet(),
    noneLabel: String = "None",
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val nodes = remember(tree, exclude) { tree.allInTreeOrder(exclude) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                PickerRow(noneLabel, 0, selected == null, muted = true) { onPick(null); onDismiss() }
                for (n in nodes) {
                    PickerRow(n.goal.title, n.depth, selected == n.goal.id) { onPick(n.goal.id); onDismiss() }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PickerRow(label: String, depth: Int, selected: Boolean, muted: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .background(if (selected) Mono.G100 else Color.Transparent)
            .padding(start = (8 + depth * 16).dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (depth > 0) Text("└ ", color = Mono.G400)
        Text(
            label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (muted) Mono.G500 else Mono.Ink,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (selected) Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
    }
}
