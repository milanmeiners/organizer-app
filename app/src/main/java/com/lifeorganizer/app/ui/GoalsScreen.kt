package com.lifeorganizer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import com.lifeorganizer.app.data.AppData
import com.lifeorganizer.app.data.ChildGoalHandling
import com.lifeorganizer.app.data.Goal
import com.lifeorganizer.app.data.GoalStatus
import com.lifeorganizer.app.data.LinkedTaskHandling
import com.lifeorganizer.app.data.Priority
import com.lifeorganizer.app.data.isOpen
import com.lifeorganizer.app.domain.GoalNode
import com.lifeorganizer.app.domain.GoalTree
import java.time.temporal.ChronoUnit

// ============================================================== Overview

@Composable
fun GoalsScreen(vm: MainViewModel, data: AppData) {
    val tree = remember(data) { GoalTree(data.goals) }
    val statuses = if (vm.showCompletedGoals) setOf(GoalStatus.ACTIVE, GoalStatus.COMPLETED) else setOf(GoalStatus.ACTIVE)
    val nodes = remember(tree, vm.expandedGoals, statuses) { tree.flatten(vm.expandedGoals, statuses) }
    val archivedRoots = remember(tree) {
        // Archived goals whose parent is not archived (or top-level) – shown with their subtree.
        data.goals.filter { g -> g.status == GoalStatus.ARCHIVED && (tree.get(g.parentId)?.status ?: GoalStatus.ACTIVE) != GoalStatus.ARCHIVED }
    }
    val archivedNodes = remember(tree, vm.expandedGoals, archivedRoots) {
        tree.flatten(vm.expandedGoals, GoalStatus.entries.toSet(), rootsOverride = archivedRoots).distinctBy { it.goal.id }
    }
    val taskCount = remember(data) { data.tasks.filter { it.goalId != null && !it.archived }.groupBy { it.goalId!! } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonoChip("Show completed", vm.showCompletedGoals, { vm.showCompletedGoals = !vm.showCompletedGoals })
                MonoChip("Expand all", false, { vm.expandedGoals = data.goals.map { it.id }.toSet() })
                MonoChip("Collapse", false, { vm.expandedGoals = emptySet() })
            }
            ThinDivider()
        }
        if (nodes.isEmpty()) {
            item {
                Column(Modifier.padding(24.dp)) {
                    Text("No goals yet.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Create a top-level goal with +, then break it down into subgoals and link tasks to it.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(nodes, key = { "g" + it.goal.id }) { n ->
            GoalTreeRow(vm, tree, n, taskCount[n.goal.id]?.size ?: 0)
        }
        if (archivedRoots.isNotEmpty()) {
            item {
                SectionHeader("Archived (${data.goals.count { it.status == GoalStatus.ARCHIVED }})") {
                    TextButton(onClick = { vm.showArchivedGoals = !vm.showArchivedGoals }) { Text(if (vm.showArchivedGoals) "Hide" else "Show") }
                }
            }
            if (vm.showArchivedGoals) {
                items(archivedNodes, key = { "a" + it.goal.id }) { n -> GoalTreeRow(vm, tree, n, taskCount[n.goal.id]?.size ?: 0) }
            }
        }
    }
}

@Composable
private fun GoalTreeRow(vm: MainViewModel, tree: GoalTree, n: GoalNode, tasks: Int) {
    val g = n.goal
    val p = tree.progress(g.id)
    val expanded = g.id in vm.expandedGoals
    val done = g.status == GoalStatus.COMPLETED
    val archived = g.status == GoalStatus.ARCHIVED
    Row(
        Modifier.fillMaxWidth().clickable { vm.navigate(Screen.GoalDetail(g.id)) }
            .padding(start = (4 + n.depth * 20).dp, end = 16.dp).heightIn(min = 60.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (n.hasChildren) {
            IconButton(onClick = { vm.toggleGoalExpanded(g.id) }) {
                Icon(Icons.Filled.ExpandMore, if (expanded) "Collapse" else "Expand", modifier = Modifier.rotate(if (expanded) 0f else -90f))
            }
        } else {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (done) Mono.Black else Mono.G300))
            }
        }
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    g.title,
                    style = if (n.depth == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    color = if (done || archived) Mono.G500 else Mono.Ink,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (done) Icon(Icons.Filled.Check, "Completed", modifier = Modifier.padding(start = 6.dp).size(16.dp))
                PriorityMark(g.priority, Modifier.padding(start = 6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                ProgressLine(p.percent, Modifier.weight(1f), height = if (n.depth == 0) 4.dp else 3.dp)
                Text(
                    (if (p.calculated) "≈" else "") + "${p.percent}%",
                    fontSize = 11.sp, color = Mono.G600, modifier = Modifier.padding(start = 8.dp).width(40.dp),
                )
            }
            val meta = buildList {
                g.deadline?.let { add("Due " + Fmt.date(it, vm.today)) }
                if (tasks > 0) add("$tasks task" + if (tasks == 1) "" else "s")
                if (archived) add("Archived")
            }
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), fontSize = 12.sp, color = Mono.G600, modifier = Modifier.padding(top = 2.dp))
        }
    }
    ThinDivider(start = (16 + n.depth * 20).dp)
}

// ============================================================== Detail

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalDetailScreen(vm: MainViewModel, data: AppData, screen: Screen.GoalDetail) {
    val tree = remember(data) { GoalTree(data.goals) }
    val g = tree.get(screen.id)
    var confirmDelete by remember { mutableStateOf(false) }
    var linkTask by remember { mutableStateOf(false) }
    var progressDraft by remember(g?.manualProgress) { mutableStateOf(g?.manualProgress ?: 0) }

    Column(Modifier.fillMaxSize().background(Mono.White).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f))
            if (g != null) TextButton(onClick = { vm.navigate(Screen.EditGoal(id = g.id)) }) { Text("Edit", fontWeight = FontWeight.Bold) }
        }
        if (g == null) {
            Text("This goal no longer exists.", Modifier.padding(20.dp))
            return@Column
        }
        val p = tree.progress(g.id)
        val children = tree.children(g.id)
        val tasks = data.tasks.filter { it.goalId == g.id }.sortedWith(compareBy({ !it.isOpen }, { it.deadline ?: java.time.LocalDate.MAX }, { it.title.lowercase() }))
        val path = tree.path(g.id).dropLast(1)

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                if (path.isNotEmpty()) {
                    FlowRow(verticalArrangement = Arrangement.Center) {
                        path.forEachIndexed { i, pg ->
                            Text(
                                pg.title, fontSize = 12.sp, color = Mono.G600,
                                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { vm.replace(Screen.GoalDetail(pg.id)) }.padding(vertical = 4.dp, horizontal = 2.dp),
                            )
                            Text(if (i < path.lastIndex) " › " else " ›", fontSize = 12.sp, color = Mono.G400, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                Text(g.title, style = MaterialTheme.typography.headlineSmall,
                    textDecoration = if (g.status == GoalStatus.COMPLETED) TextDecoration.LineThrough else null)
                val meta = buildList {
                    add(when (g.status) { GoalStatus.ACTIVE -> "Active"; GoalStatus.COMPLETED -> "Completed"; GoalStatus.ARCHIVED -> "Archived" })
                    if (g.priority > 0) add("Priority " + Priority.label(g.priority).lowercase())
                    g.deadline?.let {
                        val days = ChronoUnit.DAYS.between(vm.today, it)
                        add("Deadline " + Fmt.date(it, vm.today) + if (days < 0 && g.status == GoalStatus.ACTIVE) " (passed)" else "")
                    }
                }
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                if (g.description.isNotBlank()) Text(g.description, modifier = Modifier.padding(top = 12.dp))
            }

            SectionHeader("Progress")
            Column(Modifier.padding(horizontal = 20.dp)) {
                if (p.calculated) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressLine(p.percent, Modifier.weight(1f), height = 6.dp)
                        Text("${p.percent}%", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
                    }
                    Text("Calculated · average of ${tree.activeChildren(g.id).size} subgoals (equal weight). Indicative only.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                } else if (g.status == GoalStatus.COMPLETED) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressLine(100, Modifier.weight(1f), height = 6.dp)
                        Text("100%", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
                    }
                } else {
                    ProgressSlider(progressDraft, { progressDraft = it }, onFinished = {
                        vm.run { saveGoal(g.copy(manualProgress = progressDraft)) }
                    })
                    Text("Manual · set by you", style = MaterialTheme.typography.bodySmall)
                }
            }
            FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (g.status == GoalStatus.COMPLETED) {
                    DangerButton("Reopen", { vm.run { saveGoal(g.copy(status = GoalStatus.ACTIVE)) } })
                } else if (g.status == GoalStatus.ACTIVE) {
                    DangerButton("Mark completed", { vm.run { saveGoal(g.copy(status = GoalStatus.COMPLETED)) } })
                }
                if (g.status == GoalStatus.ARCHIVED) {
                    DangerButton("Unarchive", { vm.run { saveGoal(g.copy(status = GoalStatus.ACTIVE)) } })
                } else {
                    DangerButton("Archive", { vm.run { saveGoal(g.copy(status = GoalStatus.ARCHIVED)) } })
                }
                DangerButton("Delete", { confirmDelete = true })
            }

            SectionHeader("Subgoals · ${children.size}") {
                TextButton(onClick = { vm.navigate(Screen.EditGoal(parentId = g.id)) }) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Subgoal")
                }
            }
            if (children.isEmpty()) Text("No subgoals.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
            for (c in children) {
                val cp = tree.progress(c.id)
                Row(
                    Modifier.fillMaxWidth().clickable { vm.navigate(Screen.GoalDetail(c.id)) }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            c.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = if (c.status == GoalStatus.ACTIVE) Mono.Ink else Mono.G500,
                            textDecoration = if (c.status == GoalStatus.COMPLETED) TextDecoration.LineThrough else null,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            ProgressLine(cp.percent, Modifier.weight(1f))
                            Text((if (cp.calculated) "≈" else "") + "${cp.percent}%" + if (c.status == GoalStatus.ARCHIVED) " · archived" else "",
                                fontSize = 11.sp, color = Mono.G600, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Mono.G400)
                }
                ThinDivider(start = 20.dp)
            }

            SectionHeader("Tasks · ${tasks.count { it.isOpen }} open") {
                Row {
                    TextButton(onClick = { linkTask = true }) { Icon(Icons.Filled.Link, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Link") }
                    TextButton(onClick = { vm.navigate(Screen.EditTask(goalId = g.id)) }) { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Task") }
                }
            }
            if (tasks.isEmpty()) Text("No tasks linked to this goal.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
            for (t in tasks) TaskRow(vm, data, t, tree, showGoal = false)

            if (g.notes.isNotBlank()) {
                SectionHeader("Notes")
                Text(g.notes, modifier = Modifier.padding(horizontal = 20.dp))
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (g != null && confirmDelete) DeleteGoalDialog(vm, data, tree, g) { confirmDelete = false }
    if (g != null && linkTask) LinkTaskDialog(vm, data, g) { linkTask = false }
}

@Composable
private fun DeleteGoalDialog(vm: MainViewModel, data: AppData, tree: GoalTree, g: Goal, onDismiss: () -> Unit) {
    val descendants = remember(tree, g) { tree.descendants(g.id) }
    var children by remember { mutableStateOf(ChildGoalHandling.MOVE_UP) }
    var tasks by remember { mutableStateOf(LinkedTaskHandling.UNLINK) }
    val affectedGoals = if (children == ChildGoalHandling.DELETE) descendants + g.id else setOf(g.id)
    val taskCount = data.tasks.count { it.goalId != null && it.goalId in affectedGoals }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete “${g.title}”?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (descendants.isNotEmpty()) {
                    Text("This goal has ${descendants.size} subgoal(s).", fontWeight = FontWeight.SemiBold)
                    RadioRow("Keep subgoals (move them up one level)", children == ChildGoalHandling.MOVE_UP) { children = ChildGoalHandling.MOVE_UP }
                    RadioRow("Delete all subgoals too", children == ChildGoalHandling.DELETE) { children = ChildGoalHandling.DELETE }
                    Spacer(Modifier.height(12.dp))
                }
                if (taskCount > 0) {
                    Text("$taskCount linked task(s).", fontWeight = FontWeight.SemiBold)
                    RadioRow("Keep tasks (just unlink them)", tasks == LinkedTaskHandling.UNLINK) { tasks = LinkedTaskHandling.UNLINK }
                    RadioRow("Delete these tasks", tasks == LinkedTaskHandling.DELETE) { tasks = LinkedTaskHandling.DELETE }
                    Spacer(Modifier.height(12.dp))
                }
                Text("Deleting cannot be undone. Archiving keeps everything.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parent = g.parentId
                vm.run(onSuccess = {
                    onDismiss()
                    if (parent != null && tree.get(parent) != null) vm.replace(Screen.GoalDetail(parent)) else vm.back()
                }) { deleteGoal(g.id, children, tasks) }
            }) { Text("Delete") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.run { saveGoal(g.copy(status = GoalStatus.ARCHIVED)) }; onDismiss() }) { Text("Archive instead") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick, colors = RadioButtonDefaults.colors(selectedColor = Mono.Black, unselectedColor = Mono.G500))
        Text(label)
    }
}

@Composable
private fun LinkTaskDialog(vm: MainViewModel, data: AppData, g: Goal, onDismiss: () -> Unit) {
    val candidates = remember(data, g) { data.tasks.filter { it.isOpen && it.goalId != g.id }.sortedBy { it.title.lowercase() } }
    val tree = remember(data) { GoalTree(data.goals) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link a task") },
        text = {
            if (candidates.isEmpty()) Text("No other open tasks.")
            else Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                for (t in candidates) {
                    Column(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                            .clickable { vm.run { saveTask(t.copy(goalId = g.id)) }; onDismiss() }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                    ) {
                        Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        tree.get(t.goalId)?.let { Text("Currently: " + it.title, fontSize = 12.sp, color = Mono.G600) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
