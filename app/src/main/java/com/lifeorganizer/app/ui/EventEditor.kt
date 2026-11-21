package com.lifeorganizer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifeorganizer.app.data.AppData
import com.lifeorganizer.app.data.Event
import com.lifeorganizer.app.data.EventException
import com.lifeorganizer.app.data.isRecurring
import com.lifeorganizer.app.domain.Planner
import java.time.LocalTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditor(vm: MainViewModel, data: AppData, screen: Screen.EditEvent) {
    val existing = screen.id?.let { id -> data.events.find { it.id == id } }
    val today = vm.today
    val occDate = screen.occurrenceDate
    val isOccurrence = existing != null && existing.isRecurring && occDate != null

    // When opening a single occurrence, prefill with that occurrence's (possibly modified) values.
    val initial: Event = remember(screen, existing != null) {
        when {
            existing == null -> {
                val start = screen.start ?: LocalTime.of(9, 0)
                Event(title = "", date = screen.date ?: today, startTime = start, endTime = minOf(start.plusHours(1), LocalTime.of(23, 59)).let { if (it <= start) LocalTime.of(23, 59) else it })
            }
            isOccurrence -> {
                val inst = Planner.eventsInRange(listOf(existing), data.exceptions, occDate!!.minusDays(400), occDate.plusDays(400))
                    .find { it.originalDate == occDate }
                if (inst == null) existing else existing.copy(
                    title = inst.title, description = inst.description, location = inst.location,
                    date = inst.date, startTime = inst.start, endTime = inst.end,
                )
            }
            else -> existing
        }
    }
    var e by remember(initial) { mutableStateOf(initial) }
    var triedSave by remember { mutableStateOf(false) }
    var askScope by remember { mutableStateOf<String?>(null) } // "save" or "delete"
    var confirmDelete by remember { mutableStateOf(false) }

    val timeError = e.endTime <= e.startTime
    val titleError = triedSave && e.title.isBlank()

    if (screen.id != null && existing == null) {
        EditorFrame("Event", onClose = { vm.back() }, onSave = null) { Text("This event no longer exists.", Modifier.padding(20.dp)) }
        return
    }

    fun saveSeries() {
        val base = existing ?: e
        // Editing the series from an occurrence keeps the series' original start date.
        val toSave = if (isOccurrence) e.copy(id = base.id, date = base.date) else e
        vm.run(onSuccess = { vm.back() }) { saveEvent(toSave) }
    }

    fun saveOccurrenceOnly() {
        val ex = data.exceptions.find { it.eventId == existing!!.id && it.originalDate == occDate }
        val updated = (ex ?: EventException(existing!!.id, occDate!!)).copy(
            cancelled = false, date = e.date, startTime = e.startTime, endTime = e.endTime,
            title = e.title.trim(), description = e.description, location = e.location,
        )
        vm.run(onSuccess = { vm.back() }) {
            require(e.title.isNotBlank()) { "Title is required" }
            require(e.endTime > e.startTime) { "End time must be after start time" }
            saveEventException(updated)
        }
    }

    fun onSave() {
        triedSave = true
        if (e.title.isBlank() || timeError) return
        if (isOccurrence) askScope = "save" else saveSeries()
    }

    EditorFrame(if (existing == null) "New event" else if (isOccurrence) "Edit event" else "Edit event", onClose = { vm.back() }, onSave = { onSave() }) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            MonoTextField(e.title, { e = e.copy(title = it) }, "Title", isError = titleError, supportingText = if (titleError) "A title is required" else null)
            Spacer(Modifier.height(8.dp))
            MonoTextField(e.location, { e = e.copy(location = it) }, "Location (optional)")
            Spacer(Modifier.height(8.dp))
            MonoTextField(e.description, { e = e.copy(description = it) }, "Description (optional)", singleLine = false, minLines = 2)
        }
        SectionHeader("Time")
        DateFieldRow("Date", e.date, today, { d -> if (d != null) e = e.copy(date = d) }, clearable = false)
        TimeFieldRow("Starts", e.startTime) { s ->
            val duration = java.time.Duration.between(e.startTime, e.endTime)
            var end = s.plus(duration)
            if (end <= s) end = LocalTime.of(23, 59)
            e = e.copy(startTime = s, endTime = end)
        }
        TimeFieldRow("Ends", e.endTime) { e = e.copy(endTime = it) }
        if (timeError) Text("The end time must be after the start time.", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))

        if (!isOccurrence) {
            SectionHeader("Repeat")
            RecurrenceEditor(e.rrule, e.date, today) { e = e.copy(rrule = it) }
        } else {
            SectionHeader("Repeat")
            Text(
                com.lifeorganizer.app.domain.Recurrence.describe(existing!!.rrule, existing.date) +
                    ". You are editing the occurrence of " + occDate!!.format(Fmt.dayMedium) + ". On save you can choose to change only this occurrence or the whole series.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        if (existing != null) {
            FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isOccurrence) {
                    DangerButton("Skip / delete this occurrence", { askScope = "delete" })
                } else {
                    DangerButton("Delete event", { confirmDelete = true })
                }
            }
        }
    }

    when (askScope) {
        "save" -> ChoiceDialog(
            "Save recurring event", "Apply the changes to…",
            listOf(
                "Only this occurrence" to { saveOccurrenceOnly() },
                "The entire series" to { saveSeries() },
            ),
            onDismiss = { askScope = null },
        )
        "delete" -> ChoiceDialog(
            "Delete recurring event", null,
            listOf(
                "Only this occurrence" to {
                    val ex = data.exceptions.find { it.eventId == existing!!.id && it.originalDate == occDate }
                    val c = (ex ?: EventException(existing!!.id, occDate!!)).copy(cancelled = true)
                    vm.run(onSuccess = { vm.back() }) { saveEventException(c) }
                },
                "The entire series" to { vm.run(onSuccess = { vm.back() }) { deleteEvent(existing!!) } },
            ),
            onDismiss = { askScope = null },
        )
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog("Delete event?", "This cannot be undone.", "Delete",
            onConfirm = { vm.run(onSuccess = { vm.back() }) { deleteEvent(existing) } }, onDismiss = { confirmDelete = false })
    }
}
