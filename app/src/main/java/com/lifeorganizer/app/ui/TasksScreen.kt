package com.lifeorganizer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeorganizer.app.data.AppData
import com.lifeorganizer.app.data.Priority
import com.lifeorganizer.app.data.Task
import com.lifeorganizer.app.data.isOpen
import com.lifeorganizer.app.data.isRecurring
import com.lifeorganizer.app.domain.DeadlineState
import com.lifeorganizer.app.domain.GoalTree
import com.lifeorganizer.app.domain.Planner
import com.lifeorganizer.app.domain.Recurrence
import com.lifeorganizer.app.domain.TaskQueries
import com.lifeorganizer.app.domain.TaskSection
import com.lifeorganizer.app.domain.TaskSort

@Composable
fun TasksScreen(vm: MainViewModel, data: AppData) {
    val goals = remember(data) { GoalTree(data.goals) }
    val list = remember(data, vm.taskSection, vm.taskFilter, vm.taskSort, vm.today) {
        TaskQueries.query(data.tasks, vm.taskSection, vm.taskFilter, vm.taskSort, vm.today)
    }
    val counts = remember(data, vm.today) {
        TaskSection.entries.associateWith { s -> data.tasks.count { TaskQueries.inSection(it, s, vm.today) } }
    }
    var sortMenu by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (s in TaskSection.entries) {
                val n = counts[s] ?: 0
                MonoChip(if (n > 0) "${s.label} $n" else s.label, vm.taskSection == s, { vm.taskSection = s })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { sortMenu = true }) {
                    Icon(Icons.Filled.Sort, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Sort: ${vm.taskSort.label}")
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    for (s in TaskSort.entries) {
                        DropdownMenuItem(
                            text = { Text(s.label, fontWeight = if (s == vm.taskSort) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { vm.taskSort = s; sortMenu = false },
                        )
                    }
                }
            }
            TextButton(onClick = { showFilter = true }) {
                Icon(Icons.Filled.FilterList, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text(if (vm.taskFilter.isActive) "Filter · on" else "Filter")
            }
            Spacer(Modifier.weight(1f))
            if (vm.taskFilter.isActive) TextButton(onClick = { vm.taskFilter = com.lifeorganizer.app.domain.TaskFilter() }) { Text("Clear") }
        }
        ThinDivider()
        if (list.isEmpty()) {
            Text(
                when (vm.taskSection) {
                    TaskSection.OVERDUE -> "Nothing overdue."
                    TaskSection.COMPLETED -> "No completed tasks yet."
                    TaskSection.ARCHIVED -> "No archived tasks."
                    else -> "No tasks here. Use + to create one."
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            items(list, key = { it.id }) { t -> TaskRow(vm, data, t, goals) }
        }
    }

    if (showFilter) FilterDialog(vm, data, goals) { showFilter = false }
}

/** A task line used in the task overview and on goal pages. */
@Composable
fun TaskRow(vm: MainViewModel, data: AppData, t: Task, goals: GoalTree, showGoal: Boolean = true) {
    val today = vm.today
    val todayOcc = if (t.isRecurring && t.isOpen) remember(data, t, today) { Planner.occurrenceOn(t, data.occurrences, today) } else null
    Row(
        Modifier.fillMaxWidth().background(Mono.White).clickable { vm.navigate(Screen.EditTask(id = t.id)) }
            .padding(start = 8.dp, end = 16.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            t.isRecurring && todayOcc != null -> MonoCheck(todayOcc.done, round = true, onToggle = { toggleDayItem(vm, todayOcc) })
            t.isRecurring -> Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Repeat, "Recurring", tint = Mono.G500, modifier = Modifier.size(18.dp))
            }
            else -> MonoCheck(t.completed, onToggle = { vm.run { setTaskCompleted(t, !t.completed) } })
        }
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StrikeText(t.title, t.completed || (todayOcc?.done == true), Modifier.weight(1f, fill = false))
                PriorityMark(t.priority, Modifier.padding(start = 6.dp))
            }
            val meta = buildList {
                if (t.isRecurring) {
                    add(Recurrence.describe(t.rrule, t.startDate))
                    if (todayOcc == null && t.isOpen) Planner.nextOpenOccurrence(t, data.occurrences, today)?.let { add("Next " + Fmt.date(it, today)) }
                }
                if (!t.isRecurring) t.assignedDate?.let { add("Planned " + Fmt.date(it, today)) }
                if (showGoal) goals.get(t.goalId)?.let { add("◎ " + it.title) }
                if (t.category.isNotBlank()) add(t.category)
                if (t.workFrequency.isNotBlank()) add(t.workFrequency)
            }
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), fontSize = 12.sp, color = Mono.G600, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (t.trackProgress) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 5.dp)) {
                    ProgressLine(t.progress, Modifier.weight(1f))
                    Text("${t.progress}%", fontSize = 11.sp, color = Mono.G600, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        t.deadline?.let { d ->
            val state = TaskQueries.deadlineState(t, today)
            DeadlineBadge(Fmt.deadline(d, today).removePrefix("Due "), state)
        }
    }
    ThinDivider(start = 56.dp)
}

@Composable
fun DeadlineBadge(text: String, state: DeadlineState) {
    val strong = state == DeadlineState.OVERDUE || state == DeadlineState.TODAY
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    Text(
        text,
        fontSize = 11.sp,
        maxLines = 1,
        fontWeight = if (strong || state == DeadlineState.SOON) FontWeight.SemiBold else FontWeight.Normal,
        color = when { strong -> Mono.White; state == DeadlineState.SOON -> Mono.Black; else -> Mono.G600 },
        modifier = Modifier.padding(start = 8.dp)
            .then(
                when {
                    strong -> Modifier.background(Mono.Black, shape)
                    state == DeadlineState.SOON -> Modifier.background(Mono.G200, shape)
                    else -> Modifier
                }
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterDialog(vm: MainViewModel, data: AppData, goals: GoalTree, onDismiss: () -> Unit) {
    var f by remember { mutableStateOf(vm.taskFilter) }
    var pickGoal by remember { mutableStateOf(false) }
    val categories = remember(data) { TaskQueries.categories(data.tasks) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Priority", style = SectionLabel, modifier = Modifier.padding(bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MonoChip("Any", f.priority == null, { f = f.copy(priority = null) })
                    for (p in listOf(Priority.HIGH, Priority.MEDIUM, Priority.LOW, Priority.NONE)) {
                        MonoChip(Priority.label(p), f.priority == p, { f = f.copy(priority = p) })
                    }
                }
                Text("Recurrence", style = SectionLabel, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MonoChip("Any", f.recurring == null, { f = f.copy(recurring = null) })
                    MonoChip("Recurring", f.recurring == true, { f = f.copy(recurring = true) })
                    MonoChip("One-time", f.recurring == false, { f = f.copy(recurring = false) })
                }
                Text("Category", style = SectionLabel, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MonoChip("Any", f.category == null, { f = f.copy(category = null) })
                    for (c in categories) MonoChip(c, f.category.equals(c, ignoreCase = true), { f = f.copy(category = c) })
                }
                Text("Goal", style = SectionLabel, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                MonoChip(goals.get(f.goalId)?.title ?: "Any goal", f.goalId != null, { pickGoal = true })
            }
        },
        confirmButton = { TextButton(onClick = { vm.taskFilter = f; onDismiss() }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickGoal) {
        GoalPickerDialog(goals, "Filter by goal", f.goalId, noneLabel = "Any goal", onPick = { f = f.copy(goalId = it) }, onDismiss = { pickGoal = false })
    }
}
