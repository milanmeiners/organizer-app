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
import androidx.compose.ui.unit.dp
import com.lifeorganizer.app.data.AppData
import com.lifeorganizer.app.data.Goal
import com.lifeorganizer.app.data.GoalStatus
import com.lifeorganizer.app.domain.GoalTree

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalEditor(vm: MainViewModel, data: AppData, screen: Screen.EditGoal) {
    val tree = remember(data) { GoalTree(data.goals) }
    val existing = tree.get(screen.id)
    var g by remember(screen.id, existing != null) {
        mutableStateOf(existing ?: Goal(title = "", parentId = screen.parentId))
    }
    var triedSave by remember { mutableStateOf(false) }
    var pickParent by remember { mutableStateOf(false) }
    val titleError = triedSave && g.title.isBlank()
    val hasActiveChildren = existing != null && tree.activeChildren(existing.id).isNotEmpty()

    if (screen.id != null && existing == null) {
        EditorFrame("Goal", onClose = { vm.back() }, onSave = null) { Text("This goal no longer exists.", Modifier.padding(20.dp)) }
        return
    }

    fun save() {
        triedSave = true
        if (g.title.isBlank()) return
        val isNew = existing == null
        vm.run(onSuccess = { if (!isNew) vm.back() }) {
            val id = saveGoal(g)
            if (isNew) vm.replace(Screen.GoalDetail(id))
        }
    }

    EditorFrame(if (existing == null) (if (g.parentId == null) "New goal" else "New subgoal") else "Edit goal", onClose = { vm.back() }, onSave = { save() }) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            MonoTextField(g.title, { g = g.copy(title = it) }, "Goal title", isError = titleError, supportingText = if (titleError) "A title is required" else null)
            Spacer(Modifier.height(8.dp))
            MonoTextField(g.description, { g = g.copy(description = it) }, "Description (optional)", singleLine = false, minLines = 2)
        }

        SectionHeader("Place in hierarchy")
        FieldRow(
            "Parent goal",
            tree.get(g.parentId)?.let { tree.path(it.id).joinToString(" › ") { p -> p.title } } ?: "None (top-level goal)",
            onClick = { pickParent = true },
            onClear = if (g.parentId != null) ({ g = g.copy(parentId = null) }) else null,
            muted = g.parentId == null,
        )

        SectionHeader("Details")
        DateFieldRow("Deadline", g.deadline, vm.today, { g = g.copy(deadline = it) })
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Priority", style = SectionLabel, modifier = Modifier.padding(bottom = 6.dp))
            PriorityPicker(g.priority) { g = g.copy(priority = it) }
            Text("Status", style = SectionLabel, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MonoChip("Active", g.status == GoalStatus.ACTIVE, { g = g.copy(status = GoalStatus.ACTIVE) })
                MonoChip("Completed", g.status == GoalStatus.COMPLETED, { g = g.copy(status = GoalStatus.COMPLETED) })
                MonoChip("Archived", g.status == GoalStatus.ARCHIVED, { g = g.copy(status = GoalStatus.ARCHIVED) })
            }
        }

        SectionHeader("Progress")
        if (hasActiveChildren) {
            Text(
                "Progress is calculated from the subgoals (equal-weight average).",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else {
            ProgressSlider(g.manualProgress ?: 0, { g = g.copy(manualProgress = it) }, modifier = Modifier.padding(horizontal = 20.dp))
            Text("Set manually. Once you add subgoals, progress is calculated from them.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            MonoTextField(g.notes, { g = g.copy(notes = it) }, "Notes (optional)", singleLine = false, minLines = 3)
        }
    }

    if (pickParent) {
        // A goal can never be moved below itself or one of its own subgoals.
        val exclude = if (existing != null) tree.descendants(existing.id) + existing.id else emptySet()
        GoalPickerDialog(tree, "Parent goal", g.parentId, exclude = exclude, noneLabel = "None (top-level goal)",
            onPick = { g = g.copy(parentId = it) }, onDismiss = { pickParent = false })
    }
}
