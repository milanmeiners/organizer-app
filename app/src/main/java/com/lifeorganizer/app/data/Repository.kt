package com.lifeorganizer.app.data

import androidx.room.withTransaction
import com.lifeorganizer.app.domain.GoalTree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

enum class ChildGoalHandling { MOVE_UP, DELETE }
enum class LinkedTaskHandling { UNLINK, DELETE }

class Repository(private val db: AppDatabase) {
    private val dao = db.dao()

    val data: Flow<AppData> = combine(
        combine(dao.observeTasks(), dao.observeOccurrences(), dao.observeEvents()) { t, o, e -> Triple(t, o, e) },
        combine(dao.observeExceptions(), dao.observeGoals(), dao.observeDayOrders()) { x, g, d -> Triple(x, g, d) },
    ) { a, b ->
        AppData(
            tasks = a.first, occurrences = a.second, events = a.third,
            exceptions = b.first, goals = b.second, dayOrders = b.third,
        )
    }

    // ---------------- Tasks ----------------

    suspend fun saveTask(task: Task): Long {
        val t = task.copy(title = task.title.trim(), progress = task.progress.coerceIn(0, 100))
        require(t.title.isNotEmpty()) { "Title is required" }
        return dao.saveTask(t)
    }

    suspend fun setTaskCompleted(task: Task, done: Boolean) {
        dao.saveTask(task.copy(completed = done, completedAt = if (done) System.currentTimeMillis() else null))
    }

    suspend fun setTaskProgress(task: Task, progress: Int) {
        dao.saveTask(task.copy(progress = progress.coerceIn(0, 100)))
    }

    suspend fun deleteTask(task: Task) = db.withTransaction {
        dao.deleteOccurrencesOf(task.id)
        dao.deleteDayOrderKeys("t:${task.id}", "o:${task.id}:%")
        dao.deleteTask(task)
    }

    suspend fun saveOccurrence(o: TaskOccurrence) = dao.saveOccurrence(o)

    suspend fun setOccurrenceStatus(task: Task, date: LocalDate, current: TaskOccurrence?, status: OccurrenceStatus) {
        val base = current ?: TaskOccurrence(taskId = task.id, date = date)
        dao.saveOccurrence(base.copy(status = status))
    }

    suspend fun saveDayOrder(date: LocalDate, keys: List<String>) = db.withTransaction {
        dao.clearDayOrder(date)
        dao.saveDayOrders(keys.mapIndexed { i, k -> DayOrder(date, k, i) })
    }

    // ---------------- Events ----------------

    suspend fun saveEvent(e: Event): Long {
        val ev = e.copy(title = e.title.trim())
        require(ev.title.isNotEmpty()) { "Title is required" }
        require(ev.endTime > ev.startTime) { "End time must be after start time" }
        return dao.saveEvent(ev)
    }

    suspend fun deleteEvent(e: Event) = db.withTransaction {
        dao.deleteExceptionsOf(e.id)
        dao.deleteEvent(e.id)
    }

    suspend fun saveEventException(ex: EventException) = dao.saveException(ex)

    // ---------------- Goals ----------------

    suspend fun saveGoal(goal: Goal): Long = db.withTransaction {
        val g = goal.copy(title = goal.title.trim(), manualProgress = goal.manualProgress?.coerceIn(0, 100))
        require(g.title.isNotEmpty()) { "Title is required" }
        if (g.id != 0L) {
            val tree = GoalTree(dao.allGoals())
            require(!tree.wouldCreateCycle(g.id, g.parentId)) { "A goal cannot be placed inside itself or one of its subgoals" }
        }
        if (g.parentId != null) require(dao.goal(g.parentId) != null) { "Parent goal not found" }
        dao.saveGoal(g)
    }

    suspend fun deleteGoal(goalId: Long, children: ChildGoalHandling, tasks: LinkedTaskHandling) = db.withTransaction {
        val goal = dao.goal(goalId) ?: return@withTransaction
        val tree = GoalTree(dao.allGoals())
        val affected: List<Long> = when (children) {
            ChildGoalHandling.MOVE_UP -> {
                dao.reparentChildren(goalId, goal.parentId)
                listOf(goalId)
            }
            ChildGoalHandling.DELETE -> listOf(goalId) + tree.descendants(goalId)
        }
        when (tasks) {
            LinkedTaskHandling.UNLINK -> dao.unlinkTasksFromGoals(affected)
            LinkedTaskHandling.DELETE -> {
                val ids = dao.taskIdsForGoals(affected)
                if (ids.isNotEmpty()) {
                    dao.deleteOccurrencesOfTasks(ids)
                    for (id in ids) dao.deleteDayOrderKeys("t:$id", "o:$id:%")
                    dao.deleteTasksByIds(ids)
                }
            }
        }
        dao.deleteGoalsByIds(affected)
    }

    // ---------------- Backup ----------------

    suspend fun snapshot(): AppData = AppData(
        tasks = dao.allTasks(), occurrences = dao.allOccurrences(), events = dao.allEvents(),
        exceptions = dao.allExceptions(), goals = dao.allGoals(), dayOrders = dao.allDayOrders(),
    )

    suspend fun replaceAll(data: AppData) = db.withTransaction {
        dao.wipeDayOrders(); dao.wipeOccurrences(); dao.wipeExceptions()
        dao.wipeTasks(); dao.wipeEvents(); dao.wipeGoals()
        dao.saveGoals(data.goals)
        dao.saveTasks(data.tasks)
        dao.saveOccurrences(data.occurrences)
        dao.saveEvents(data.events)
        dao.saveExceptions(data.exceptions)
        dao.saveDayOrders(data.dayOrders)
    }
}
