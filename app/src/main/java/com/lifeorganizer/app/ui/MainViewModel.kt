package com.lifeorganizer.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifeorganizer.app.data.AppData
import com.lifeorganizer.app.data.AppDatabase
import com.lifeorganizer.app.data.Repository
import com.lifeorganizer.app.domain.TaskFilter
import com.lifeorganizer.app.domain.TaskSection
import com.lifeorganizer.app.domain.TaskSort
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

sealed interface Screen {
    data object Home : Screen
    data class EditTask(val id: Long? = null, val goalId: Long? = null, val date: LocalDate? = null) : Screen
    data class EditOccurrence(val taskId: Long, val date: LocalDate) : Screen
    data class EditEvent(
        val id: Long? = null,
        /** Original date of the tapped occurrence when editing a recurring event. */
        val occurrenceDate: LocalDate? = null,
        val date: LocalDate? = null,
        val start: LocalTime? = null,
    ) : Screen
    data class EditGoal(val id: Long? = null, val parentId: Long? = null) : Screen
    data class GoalDetail(val id: Long) : Screen
}

enum class Tab(val label: String) { CALENDAR("Calendar"), TASKS("Tasks"), GOALS("Goals") }
enum class CalendarMode(val short: String) { YEAR("Y"), MONTH("M"), WEEK("W"), DAY("D") }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(AppDatabase.get(app))

    val data: StateFlow<AppData> = repo.data.stateIn(viewModelScope, SharingStarted.Eagerly, AppData())

    val backStack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = backStack.last()

    var tab by mutableStateOf(Tab.CALENDAR)
    var calendarMode by mutableStateOf(CalendarMode.MONTH)
    var selectedDate by mutableStateOf(LocalDate.now())
    var today by mutableStateOf(LocalDate.now())
        private set

    var taskSection by mutableStateOf(TaskSection.ALL_OPEN)
    var taskSort by mutableStateOf(TaskSort.DEADLINE)
    var taskFilter by mutableStateOf(TaskFilter())

    var expandedGoals by mutableStateOf(setOf<Long>())
    var showArchivedGoals by mutableStateOf(false)
    var showCompletedGoals by mutableStateOf(true)

    /** One-off message shown in a snackbar. */
    var message by mutableStateOf<String?>(null)

    fun refreshToday() {
        val now = LocalDate.now()
        if (now != today) today = now
    }

    fun navigate(screen: Screen) { backStack.add(screen) }

    fun back() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    /** Replaces the current screen (e.g. after creating a goal, open its detail page). */
    fun replace(screen: Screen) {
        if (backStack.size > 1) backStack[backStack.lastIndex] = screen else backStack.add(screen)
    }

    fun openDay(date: LocalDate) {
        selectedDate = date
        calendarMode = CalendarMode.DAY
        tab = Tab.CALENDAR
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun toggleGoalExpanded(id: Long) {
        expandedGoals = if (id in expandedGoals) expandedGoals - id else expandedGoals + id
    }

    /** Runs a repository operation; validation errors are shown to the user instead of crashing. */
    fun run(onSuccess: () -> Unit = {}, block: suspend Repository.() -> Unit) {
        viewModelScope.launch {
            try {
                repo.block()
                onSuccess()
            } catch (e: IllegalArgumentException) {
                message = e.message ?: "Invalid input"
            } catch (e: Exception) {
                message = "Something went wrong: ${e.message}"
            }
        }
    }
}
