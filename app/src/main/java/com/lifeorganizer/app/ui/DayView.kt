package com.lifeorganizer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeorganizer.app.data.AppData
import com.lifeorganizer.app.data.OccurrenceStatus
import com.lifeorganizer.app.data.Task
import com.lifeorganizer.app.data.TaskOccurrence
import com.lifeorganizer.app.data.isOpen
import com.lifeorganizer.app.data.isRecurring
import com.lifeorganizer.app.domain.DayItem
import com.lifeorganizer.app.domain.GoalTree
import com.lifeorganizer.app.domain.Planner
import java.time.LocalDate
import java.time.LocalTime

private val DAY_HOUR = 46.dp

@Composable
fun DayView(vm: MainViewModel, data: AppData) {
    val date = vm.selectedDate
    val events = remember(data, date) { Planner.eventsInRange(data.events, data.exceptions, date, date) }
    val items = remember(data, date) { Planner.dayItems(data, date) }
    val deadlines = remember(data, date) { Planner.deadlinesInRange(data.tasks, date, date)[date].orEmpty() }
    val goals = remember(data) { GoalTree(data.goals) }
    var showAdd by remember { mutableStateOf(false) }
    var moveItem by remember { mutableStateOf<DayItem?>(null) }

    val fromHour = minOf(8, events.minOfOrNull { it.start.hour } ?: 8)
    val toHour = maxOf(18, events.maxOfOrNull { if (it.end.minute > 0) it.end.hour + 1 else it.end.hour } ?: 18).coerceAtMost(24)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (deadlines.isNotEmpty()) {
            SectionHeader("Deadlines")
            for (t in deadlines) {
                Row(
                    Modifier.fillMaxWidth().clickable { vm.navigate(Screen.EditTask(id = t.id)) }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("!", fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                    Text(t.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Fmt.deadline(t.deadline!!, vm.today), fontSize = 12.sp, color = Mono.G600)
                }
            }
        }

        // ---------------- A. Fixed schedule
        SectionHeader("Schedule") {
            TextButton(onClick = { vm.navigate(Screen.EditEvent(date = date)) }) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Event")
            }
        }
        if (events.isEmpty()) {
            Text("No fixed events.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        if (events.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(end = 12.dp)) {
            Column(Modifier.width(48.dp)) {
                for (h in fromHour until toHour) {
                    Box(Modifier.height(DAY_HOUR).fillMaxWidth()) {
                        Text("%02d:00".format(h), fontSize = 10.sp, color = Mono.G400, modifier = Modifier.offset(x = 8.dp, y = (-6).dp))
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                TimeColumn(
                    events = events, hourHeight = DAY_HOUR, fromHour = fromHour, toHour = toHour, compact = false, highlight = false,
                    modifier = Modifier.fillMaxWidth(),
                    onEvent = { vm.navigate(Screen.EditEvent(id = it.event.id, occurrenceDate = if (it.recurring) it.originalDate else null)) },
                    onEmpty = { t -> vm.navigate(Screen.EditEvent(date = date, start = t)) },
                )
                if (date == vm.today) {
                    val now = LocalTime.now()
                    val mins = now.hour * 60 + now.minute - fromHour * 60
                    if (mins in 0..((toHour - fromHour) * 60)) {
                        Box(Modifier.offset(y = DAY_HOUR * (mins / 60f)).fillMaxWidth().height(1.5.dp).background(Mono.Black))
                    }
                }
            }
        }

        // ---------------- B. To-do list
        SectionHeader("To-do · ${items.count { !it.done && !it.skipped }} open") {
            Row {
                TextButton(onClick = { showAdd = true }) { Text("Add existing") }
                TextButton(onClick = { vm.navigate(Screen.EditTask(date = date)) }) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("New")
                }
            }
        }
        if (items.isEmpty()) {
            Text(
                "Nothing planned for this day. Add tasks from your list — you decide what to work on.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        ReorderableColumn(
            items = items,
            keyOf = { it.key },
            onReorder = { newList -> vm.run { saveDayOrder(date, newList.map { it.key }) } },
        ) { item, dragModifier, _ ->
            DayItemRow(vm, item, date, goals, dragModifier, onMove = { moveItem = item })
        }
        Spacer(Modifier.height(120.dp))
    }

    if (showAdd) AddExistingDialog(vm, data, date) { showAdd = false }
    moveItem?.let { item ->
        DatePickerModal(item.occurrence?.movedTo ?: item.occurrenceDate ?: item.task.assignedDate ?: date, onDismiss = { moveItem = null }) { newDate ->
            if (item.occurrenceDate == null) {
                vm.run { saveTask(item.task.copy(assignedDate = newDate)) }
            } else {
                val base = item.occurrence ?: TaskOccurrence(item.task.id, item.occurrenceDate)
                vm.run { saveOccurrence(base.copy(movedTo = if (newDate == item.occurrenceDate) null else newDate)) }
            }
        }
    }
}

@Composable
private fun DayItemRow(vm: MainViewModel, item: DayItem, date: LocalDate, goals: GoalTree, dragModifier: Modifier, onMove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val t = item.task
    Row(
        Modifier.fillMaxWidth().background(Mono.White)
            .clickable {
                if (item.occurrenceDate != null) vm.navigate(Screen.EditOccurrence(t.id, item.occurrenceDate))
                else vm.navigate(Screen.EditTask(id = t.id))
            }
            .padding(start = 8.dp, end = 0.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoCheck(item.done, round = item.occurrenceDate != null, onToggle = { toggleDayItem(vm, item) })
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StrikeText(item.title, item.done || item.skipped, Modifier.weight(1f, fill = false))
                if (item.occurrenceDate != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Repeat, "Recurring", tint = Mono.G500, modifier = Modifier.size(14.dp))
                }
                PriorityMark(t.priority, Modifier.padding(start = 6.dp))
            }
            val meta = buildList {
                if (item.skipped) add("Skipped")
                if (t.deadline != null && t.isOpen) add(Fmt.deadline(t.deadline, vm.today))
                goals.get(t.goalId)?.let { add("◎ " + it.title) }
                if (t.category.isNotBlank()) add(t.category)
            }
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), fontSize = 12.sp, color = Mono.G600, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (t.trackProgress) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    ProgressLine(t.progress, Modifier.weight(1f))
                    Text("${t.progress}%", fontSize = 11.sp, color = Mono.G600, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Mono.G600) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (item.occurrenceDate == null) {
                    DropdownMenuItem(text = { Text("Edit task") }, onClick = { menu = false; vm.navigate(Screen.EditTask(id = t.id)) })
                    DropdownMenuItem(text = { Text("Move to another day") }, onClick = { menu = false; onMove() })
                    DropdownMenuItem(text = { Text("Remove from this day") }, onClick = { menu = false; vm.run { saveTask(t.copy(assignedDate = null)) } })
                } else {
                    val od = item.occurrenceDate
                    DropdownMenuItem(text = { Text("Edit this occurrence") }, onClick = { menu = false; vm.navigate(Screen.EditOccurrence(t.id, od)) })
                    DropdownMenuItem(text = { Text("Edit entire series") }, onClick = { menu = false; vm.navigate(Screen.EditTask(id = t.id)) })
                    DropdownMenuItem(text = { Text("Move this occurrence") }, onClick = { menu = false; onMove() })
                    if (item.skipped) {
                        DropdownMenuItem(text = { Text("Un-skip") }, onClick = { menu = false; vm.run { setOccurrenceStatus(t, od, item.occurrence, OccurrenceStatus.OPEN) } })
                    } else {
                        DropdownMenuItem(text = { Text("Skip this occurrence") }, onClick = { menu = false; vm.run { setOccurrenceStatus(t, od, item.occurrence, OccurrenceStatus.SKIPPED) } })
                    }
                    DropdownMenuItem(text = { Text("Delete this occurrence") }, onClick = { menu = false; vm.run { setOccurrenceStatus(t, od, item.occurrence, OccurrenceStatus.DELETED) } })
                }
            }
        }
        Box(dragModifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.DragHandle, "Hold to reorder", tint = Mono.G400)
        }
    }
    ThinDivider(start = 56.dp)
}

fun toggleDayItem(vm: MainViewModel, item: DayItem) {
    val od = item.occurrenceDate
    if (od == null) {
        vm.run { setTaskCompleted(item.task, !item.task.completed) }
    } else {
        val next = if (item.done) OccurrenceStatus.OPEN else OccurrenceStatus.DONE
        vm.run { setOccurrenceStatus(item.task, od, item.occurrence, next) }
    }
}

@Composable
private fun AddExistingDialog(vm: MainViewModel, data: AppData, date: LocalDate, onDismiss: () -> Unit) {
    val candidates = remember(data, date) {
        data.tasks.filter { it.isOpen && !it.isRecurring && it.assignedDate != date }
            .sortedWith(compareBy<Task>({ it.deadline ?: LocalDate.MAX }, { it.title.lowercase() }))
    }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tasks to ${Fmt.date(date, vm.today)}") },
        text = {
            if (candidates.isEmpty()) {
                Text("All open tasks are already planned for this day, or there are none.")
            } else {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    for (t in candidates) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { selected = if (t.id in selected) selected - t.id else selected + t.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MonoCheck(t.id in selected, onToggle = { selected = if (t.id in selected) selected - t.id else selected + t.id })
                            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val meta = listOfNotNull(
                                    t.assignedDate?.let { "Planned " + Fmt.date(it, vm.today) },
                                    t.deadline?.let { Fmt.deadline(it, vm.today) },
                                )
                                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), fontSize = 12.sp, color = Mono.G600)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty(), onClick = {
                val chosen = candidates.filter { it.id in selected }
                vm.run { chosen.forEach { saveTask(it.copy(assignedDate = date)) } }
                onDismiss()
            }) { Text("Add ${if (selected.isEmpty()) "" else selected.size.toString()}") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
