package com.lifeorganizer.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeorganizer.app.data.AppData
import com.lifeorganizer.app.print.PdfPrinter

@Composable
fun AppRoot(vm: MainViewModel, onExport: () -> Unit, onImport: () -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    BackHandler(enabled = vm.backStack.size > 1) { vm.back() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Box(Modifier.fillMaxSize().background(Mono.White)) {
        when (val s = vm.current) {
            Screen.Home -> HomeScreen(vm, data, onExport, onImport)
            is Screen.EditTask -> TaskEditor(vm, data, s)
            is Screen.EditOccurrence -> OccurrenceEditor(vm, data, s)
            is Screen.EditEvent -> EventEditor(vm, data, s)
            is Screen.EditGoal -> GoalEditor(vm, data, s)
            is Screen.GoalDetail -> GoalDetailScreen(vm, data, s)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 90.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: MainViewModel, data: AppData, onExport: () -> Unit, onImport: () -> Unit) {
    var showPlus by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Mono.White,
        topBar = { TopArea(vm, data, onExport, onImport = { confirmImport = true }) },
        bottomBar = { BottomNav(vm) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPlus = true },
                containerColor = Mono.Black,
                contentColor = Mono.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
            ) { Icon(Icons.Filled.Add, "Create") }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.tab) {
                Tab.CALENDAR -> CalendarContent(vm, data)
                Tab.TASKS -> TasksScreen(vm, data)
                Tab.GOALS -> GoalsScreen(vm, data)
            }
        }
    }

    if (showPlus) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showPlus = false }, sheetState = sheet, containerColor = Mono.White, shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)) {
            val presetDate = if (vm.tab == Tab.CALENDAR && vm.calendarMode == CalendarMode.DAY) vm.selectedDate else null
            Column(Modifier.padding(bottom = 24.dp)) {
                Text("Create", style = SectionLabel, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                PlusOption(Icons.Outlined.CheckBox, "Task", "Something to get done — flexible, optional deadline") {
                    showPlus = false; vm.navigate(Screen.EditTask(date = presetDate))
                }
                PlusOption(Icons.Filled.Event, "Event", "A fixed appointment at a date and time") {
                    showPlus = false
                    vm.navigate(Screen.EditEvent(date = if (vm.tab == Tab.CALENDAR) vm.selectedDate else vm.today))
                }
                PlusOption(Icons.Outlined.Flag, "Goal", "A larger objective, optionally inside another goal") {
                    showPlus = false; vm.navigate(Screen.EditGoal())
                }
            }
        }
    }

    if (confirmImport) {
        ConfirmDialog(
            "Import backup?",
            "All current data in the app will be replaced by the content of the backup file.",
            "Choose file",
            onConfirm = onImport,
            onDismiss = { confirmImport = false },
        )
    }
}

@Composable
private fun PlusOption(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).border(1.dp, Mono.G300, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TopArea(vm: MainViewModel, data: AppData, onExport: () -> Unit, onImport: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Mono.White).statusBarsPadding().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val title = when (vm.tab) {
                Tab.CALENDAR -> calendarTitle(vm.calendarMode, vm.selectedDate)
                Tab.TASKS -> "Tasks"
                Tab.GOALS -> "Goals"
            }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1)
            if (vm.tab == Tab.CALENDAR) {
                TextButton(onClick = { vm.selectedDate = vm.today }) { Text("Today") }
                IconButton(onClick = { vm.selectedDate = shiftDate(vm.calendarMode, vm.selectedDate, -1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous")
                }
                IconButton(onClick = { vm.selectedDate = shiftDate(vm.calendarMode, vm.selectedDate, 1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next")
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Print / PDF: day") }, onClick = {
                        menu = false; PdfPrinter.printDay(context, data, if (vm.tab == Tab.CALENDAR) vm.selectedDate else vm.today)
                    })
                    DropdownMenuItem(text = { Text("Print / PDF: week") }, onClick = {
                        menu = false; PdfPrinter.printWeek(context, data, if (vm.tab == Tab.CALENDAR) vm.selectedDate else vm.today)
                    })
                    HorizontalDivider(color = Mono.G200)
                    DropdownMenuItem(text = { Text("Export backup…") }, onClick = { menu = false; onExport() })
                    DropdownMenuItem(text = { Text("Import backup…") }, onClick = { menu = false; onImport() })
                }
            }
        }
        if (vm.tab == Tab.CALENDAR) {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)).border(1.dp, Mono.G200, RoundedCornerShape(8.dp)),
            ) {
                for (m in CalendarMode.entries) {
                    val sel = vm.calendarMode == m
                    Box(
                        Modifier.weight(1f).height(34.dp).background(if (sel) Mono.Black else Mono.White)
                            .clickable { vm.calendarMode = m },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when (m) { CalendarMode.YEAR -> "Year"; CalendarMode.MONTH -> "Month"; CalendarMode.WEEK -> "Week"; CalendarMode.DAY -> "Day" },
                            color = if (sel) Mono.White else Mono.G800, fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(4.dp))
        }
        HorizontalDivider(thickness = 0.5.dp, color = Mono.G200)
    }
}

@Composable
private fun BottomNav(vm: MainViewModel) {
    Column {
        HorizontalDivider(thickness = 0.5.dp, color = Mono.G200)
        NavigationBar(containerColor = Mono.White, tonalElevation = 0.dp) {
            val colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Mono.Black, selectedTextColor = Mono.Black, indicatorColor = Mono.G100,
                unselectedIconColor = Mono.G500, unselectedTextColor = Mono.G500,
            )
            for (t in Tab.entries) {
                NavigationBarItem(
                    selected = vm.tab == t,
                    onClick = { vm.tab = t },
                    icon = {
                        Icon(
                            when (t) { Tab.CALENDAR -> Icons.Filled.CalendarMonth; Tab.TASKS -> Icons.Filled.TaskAlt; Tab.GOALS -> Icons.Filled.Flag },
                            t.label,
                        )
                    },
                    label = { Text(t.label) },
                    colors = colors,
                )
            }
        }
    }
}
