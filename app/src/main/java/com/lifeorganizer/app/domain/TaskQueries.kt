package com.lifeorganizer.app.domain

import com.lifeorganizer.app.data.Task
import com.lifeorganizer.app.data.isOpen
import com.lifeorganizer.app.data.isRecurring
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class TaskSection(val label: String) {
    ALL_OPEN("All open"),
    NO_DEADLINE("No deadline"),
    UPCOMING("Upcoming deadlines"),
    OVERDUE("Overdue"),
    LONG_TERM("Long-term"),
    RECURRING("Recurring"),
    COMPLETED("Completed"),
    ARCHIVED("Archived"),
}

enum class TaskSort(val label: String) {
    DEADLINE("Deadline"),
    PRIORITY("Priority"),
    CATEGORY("Category"),
    PROGRESS("Progress"),
    CREATED("Newest"),
    TITLE("Title"),
}

enum class DeadlineState { NONE, OVERDUE, TODAY, SOON, LATER }

/** Optional filters on top of a section. Null means "any". */
data class TaskFilter(
    val category: String? = null,
    val goalId: Long? = null,
    val priority: Int? = null,
    val recurring: Boolean? = null,
) {
    val isActive: Boolean get() = category != null || goalId != null || priority != null || recurring != null
}

object TaskQueries {
    const val SOON_DAYS = 3L
    const val UPCOMING_DAYS = 14L

    fun deadlineState(task: Task, today: LocalDate): DeadlineState {
        val d = task.deadline ?: return DeadlineState.NONE
        if (!task.isOpen) return DeadlineState.LATER
        return when {
            d < today -> DeadlineState.OVERDUE
            d == today -> DeadlineState.TODAY
            ChronoUnit.DAYS.between(today, d) <= SOON_DAYS -> DeadlineState.SOON
            else -> DeadlineState.LATER
        }
    }

    fun isLongTerm(t: Task): Boolean = t.trackProgress || t.workFrequency.isNotBlank()

    fun inSection(t: Task, section: TaskSection, today: LocalDate): Boolean = when (section) {
        TaskSection.ALL_OPEN -> t.isOpen
        TaskSection.NO_DEADLINE -> t.isOpen && t.deadline == null
        TaskSection.UPCOMING -> t.isOpen && t.deadline != null && t.deadline >= today
        TaskSection.OVERDUE -> t.isOpen && t.deadline != null && t.deadline < today
        TaskSection.LONG_TERM -> t.isOpen && isLongTerm(t)
        TaskSection.RECURRING -> t.isOpen && t.isRecurring
        TaskSection.COMPLETED -> t.completed && !t.archived
        TaskSection.ARCHIVED -> t.archived
    }

    fun matches(t: Task, f: TaskFilter): Boolean =
        (f.category == null || t.category.equals(f.category, ignoreCase = true)) &&
            (f.goalId == null || t.goalId == f.goalId) &&
            (f.priority == null || t.priority == f.priority) &&
            (f.recurring == null || t.isRecurring == f.recurring)

    fun sort(tasks: List<Task>, sort: TaskSort): List<Task> {
        val farFuture = LocalDate.of(9999, 12, 31)
        val cmp: Comparator<Task> = when (sort) {
            TaskSort.DEADLINE -> compareBy<Task> { it.deadline ?: farFuture }.thenByDescending { it.priority }
            TaskSort.PRIORITY -> compareByDescending<Task> { it.priority }.thenBy { it.deadline ?: farFuture }
            TaskSort.CATEGORY -> compareBy<Task> { it.category.isBlank() }.thenBy { it.category.lowercase() }
            TaskSort.PROGRESS -> compareByDescending<Task> { if (it.trackProgress) it.progress else -1 }
            TaskSort.CREATED -> compareByDescending<Task> { it.createdAt }
            TaskSort.TITLE -> compareBy<Task> { it.title.lowercase() }
        }
        return tasks.sortedWith(cmp.thenBy { it.title.lowercase() }.thenBy { it.id })
    }

    fun query(tasks: List<Task>, section: TaskSection, filter: TaskFilter, sort: TaskSort, today: LocalDate): List<Task> =
        sort(tasks.filter { inSection(it, section, today) && matches(it, filter) }, sort)

    fun categories(tasks: List<Task>): List<String> =
        tasks.map { it.category.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
}
