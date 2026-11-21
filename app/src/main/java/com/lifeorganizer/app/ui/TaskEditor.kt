package com.lifeorganizer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import com.lifeorganizer.app.data.AppData
import com.lifeorganizer.app.data.OccurrenceStatus
import com.lifeorganizer.app.data.Task
import com.lifeorganizer.app.data.TaskOccurrence
import com.lifeorganizer.app.domain.GoalTree
import com.lifeorganizer.app.domain.Recurrence
import com.lifeorganizer.app.domain.TaskQueries
import java.time.LocalDate

/** Common full-screen editor frame: close button, title, save action, scrollable body. */
@Composable
fun EditorFrame(title: String, onClose: () -> Unit, onSave: (() -> Unit)?, saveLabel: String = "Save", content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Mono.White).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (onSave != null) TextButton(onClick = onSave) { Text(saveLabel, fontWeight = FontWeight.Bold) }
        }
        ThinDivider()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            content()
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
fun DangerButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Mono.Black),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mono.G300),
    ) { Text(label) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditor(vm: MainViewModel, data: AppData, screen: Screen.EditTask) {
    val existing = screen.id?.let { id -> data.tasks.find { it.id == id } }
    val today = vm.today
    var t by remember(screen.id, existing != null) {
        mutableStateOf(existing ?: Task(title = "", assignedDate = screen.date, goalId = screen.goalId))
    }
    var triedSave by remember { mutableStateOf(false) }
    var pickGoal by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val goals = remember(data) { GoalTree(data.goals) }
    val categories = remember(data) { TaskQueries.categories(data.tasks) }

    val recurring = !t.rrule.isNullOrBlank()
    val deadlineError = t.deadline != null && t.startDate != null && t.deadline!! < t.startDate!!
    val titleError = triedSave && t.title.isBlank()

    fun save() {
        triedSave = true
        if (t.title.isBlank() || deadlineError) return
        val toSave = if (recurring) t.copy(assignedDate = null, startDate = t.startDate ?: today) else t
        vm.run(onSuccess = { vm.back() }) { saveTask(toSave) }
    }

    if (screen.id != null && existing == null) {
        EditorFrame("Task", onClose = { vm.back() }, onSave = null) {
            Text("This task no longer exists.", modifier = Modifier.padding(20.dp))
        }
        return
    }

    EditorFrame(if (existing == null) "New task" else "Edit task", onClose = { vm.back() }, onSave = { save() }) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            MonoTextField(t.title, { t = t.copy(title = it) }, "Title", isError = titleError, supportingText = if (titleError) "A title is required" else null)
            Spacer(Modifier.height(8.dp))
            MonoTextField(t.description, { t = t.copy(description = it) }, "Description (optional)", singleLine = false, minLines = 2)
        }

        SectionHeader("When")
        RecurrenceEditor(t.rrule, t.startDate ?: screen.date ?: today, today) { r ->
            t = t.copy(rrule = r, startDate = if (r != null) (t.startDate ?: t.assignedDate ?: screen.date ?: today) else t.startDate)
        }
        if (recurring) {
            DateFieldRow("Starts on", t.startDate ?: today, today, { d -> t = t.copy(startDate = d ?: today) }, clearable = false)
            Text(
                "Each occurrence appears in the to-do list of its day and is completed separately.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else {
            DateFieldRow("Planned for day", t.assignedDate, today, { t = t.copy(assignedDate = it) })
            DateFieldRow("Start date", t.startDate, today, { t = t.copy(startDate = it) })
        }
        DateFieldRow("Deadline", t.deadline, today, { t = t.copy(deadline = it) })
        if (deadlineError) Text("The deadline is before the start date.", color = Mono.Black, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
        if (t.deadline != null) Text(
            "A deadline is not an appointment — the task stays flexible.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp),
        )

        SectionHeader("Details")
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Priority", style = SectionLabel, modifier = Modifier.padding(bottom = 6.dp))
            PriorityPicker(t.priority) { t = t.copy(priority = it) }
            Spacer(Modifier.height(14.dp))
            MonoTextField(t.category, { t = t.copy(category = it) }, "Category (optional)")
            if (categories.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in categories) MonoChip(c, t.category.equals(c, ignoreCase = true), { t = t.copy(category = c) })
                }
            }
        }
        FieldRow(
            "Goal", goals.get(t.goalId)?.title ?: "None", onClick = { pickGoal = true },
            onClear = if (t.goalId != null) ({ t = t.copy(goalId = null) }) else null, muted = t.goalId == null,
        )

        SectionHeader("Progress")
        SwitchRow("Track progress", t.trackProgress, { t = t.copy(trackProgress = it) }, sub = "For long-term tasks. Progress is only set by you.")
        if (t.trackProgress) {
            ProgressSlider(t.progress, { t = t.copy(progress = it) }, modifier = Modifier.padding(horizontal = 20.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            MonoTextField(
                t.workFrequency, { t = t.copy(workFrequency = it) }, "Working frequency (optional)",
                placeholder = "e.g. 3× per week, every weekday",
                supportingText = "Just a personal note — nothing is scheduled automatically.",
            )
            Spacer(Modifier.height(8.dp))
            MonoTextField(t.notes, { t = t.copy(notes = it) }, "Notes (optional)", singleLine = false, minLines = 3)
        }

        if (existing != null) {
            SectionHeader("Status")
            SwitchRow(
                if (recurring) "Series finished" else "Completed", t.completed,
                { t = t.copy(completed = it, completedAt = if (it) System.currentTimeMillis() else null) },
                sub = if (recurring) "Hides the series; single occurrences are ticked off in the day view." else null,
            )
            FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DangerButton(if (existing.archived) "Unarchive" else "Archive", {
                    vm.run(onSuccess = { vm.back() }) { saveTask(t.copy(archived = !existing.archived)) }
                })
                DangerButton("Delete", { confirmDelete = true })
            }
        }
    }

    if (pickGoal) GoalPickerDialog(goals, "Link to goal", t.goalId, onPick = { t = t.copy(goalId = it) }, onDismiss = { pickGoal = false })
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            "Delete task?",
            if (recurring) "This deletes the whole series including all occurrences. This cannot be undone. (Tip: archive instead to keep it.)"
            else "This cannot be undone. (Tip: archive instead to keep it.)",
            "Delete",
            onConfirm = { vm.run(onSuccess = { vm.back() }) { deleteTask(existing) } },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Edit a single occurrence of a recurring task. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OccurrenceEditor(vm: MainViewModel, data: AppData, screen: Screen.EditOccurrence) {
    val task = data.tasks.find { it.id == screen.taskId }
    val stored = data.occurrences.find { it.taskId == screen.taskId && it.date == screen.date }
    var o by remember(screen, task != null) { mutableStateOf(stored ?: TaskOccurrence(screen.taskId, screen.date)) }

    if (task == null) {
        EditorFrame("Occurrence", onClose = { vm.back() }, onSave = null) { Text("This task no longer exists.", Modifier.padding(20.dp)) }
        return
    }
    EditorFrame("This occurrence", onClose = { vm.back() }, onSave = { vm.run(onSuccess = { vm.back() }) { saveOccurrence(o) } }) {
        Column(Modifier.padding(20.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            Text(Recurrence.describe(task.rrule, task.startDate) + " · occurrence of " + screen.date.format(Fmt.dayMedium), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            MonoTextField(o.titleOverride ?: "", { o = o.copy(titleOverride = it.ifBlank { null }) }, "Title for this day only", placeholder = task.title)
            Spacer(Modifier.height(8.dp))
            MonoTextField(o.notesOverride ?: "", { o = o.copy(notesOverride = it.ifBlank { null }) }, "Notes for this day", singleLine = false, minLines = 2)
            Text("Status", style = SectionLabel, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MonoChip("Open", o.status == OccurrenceStatus.OPEN, { o = o.copy(status = OccurrenceStatus.OPEN) })
                MonoChip("Done", o.status == OccurrenceStatus.DONE, { o = o.copy(status = OccurrenceStatus.DONE) })
                MonoChip("Skipped", o.status == OccurrenceStatus.SKIPPED, { o = o.copy(status = OccurrenceStatus.SKIPPED) })
            }
        }
        DateFieldRow("Show on day", o.movedTo ?: screen.date, vm.today, { d -> o = o.copy(movedTo = if (d == null || d == screen.date) null else d) }, clearable = false)
        FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DangerButton("Edit entire series", { vm.replace(Screen.EditTask(id = task.id)) })
            DangerButton("Delete this occurrence", {
                vm.run(onSuccess = { vm.back() }) { saveOccurrence(o.copy(status = OccurrenceStatus.DELETED)) }
            })
        }
    }
}
