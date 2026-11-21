package com.lifeorganizer.app.domain

import com.lifeorganizer.app.data.AppData
import com.lifeorganizer.app.data.DayOrder
import com.lifeorganizer.app.data.Event
import com.lifeorganizer.app.data.EventException
import com.lifeorganizer.app.data.OccurrenceStatus
import com.lifeorganizer.app.data.Task
import com.lifeorganizer.app.data.TaskOccurrence
import com.lifeorganizer.app.data.isOpen
import com.lifeorganizer.app.data.isRecurring
import java.time.LocalDate
import java.time.LocalTime

/** A concrete appearance of an event on a date (one-time events have exactly one). */
data class EventInstance(
    val event: Event,
    val originalDate: LocalDate,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val title: String,
    val location: String,
    val description: String,
    val isException: Boolean,
) {
    val key: String get() = "e:${event.id}:$originalDate"
    val recurring: Boolean get() = !event.rrule.isNullOrBlank()
}

/**
 * An item of a day's to-do list: either a one-time task assigned to that day or one occurrence of a
 * recurring task.
 */
data class DayItem(
    val task: Task,
    /** Original occurrence date for recurring tasks, null for one-time tasks. */
    val occurrenceDate: LocalDate?,
    val occurrence: TaskOccurrence?,
) {
    val key: String get() = if (occurrenceDate == null) "t:${task.id}" else "o:${task.id}:$occurrenceDate"
    val title: String get() = occurrence?.titleOverride?.takeIf { it.isNotBlank() } ?: task.title
    val done: Boolean get() = if (occurrenceDate == null) task.completed else occurrence?.status == OccurrenceStatus.DONE
    val skipped: Boolean get() = occurrence?.status == OccurrenceStatus.SKIPPED
}

object Planner {

    // ---------------- Events ----------------

    fun eventsInRange(events: List<Event>, exceptions: List<EventException>, from: LocalDate, to: LocalDate): List<EventInstance> {
        val exByEvent = exceptions.groupBy { it.eventId }
        val result = mutableListOf<EventInstance>()
        for (e in events) {
            val rule = RRule.parse(e.rrule)
            if (rule == null) {
                if (e.date in from..to) result += plain(e, e.date)
                continue
            }
            val exs = exByEvent[e.id].orEmpty().associateBy { it.originalDate }
            for (d in Recurrence.occurrences(rule, e.date, from, to)) {
                val ex = exs[d]
                if (ex == null) {
                    result += plain(e, d)
                } else if (!ex.cancelled) {
                    val inst = applyException(e, ex)
                    if (inst.date in from..to) result += inst
                }
            }
            // Occurrences moved INTO the range from outside of it.
            for (ex in exs.values) {
                if (ex.cancelled || ex.date == null) continue
                if (ex.originalDate in from..to) continue // already handled above
                if (ex.date !in from..to) continue
                if (!Recurrence.occursOn(rule, e.date, ex.originalDate)) continue
                result += applyException(e, ex)
            }
        }
        return result.sortedWith(compareBy<EventInstance>({ it.date }, { it.start }, { it.end }, { it.title }))
    }

    private fun plain(e: Event, d: LocalDate) = EventInstance(
        e, d, d, e.startTime, e.endTime, e.title, e.location, e.description, isException = false,
    )

    private fun applyException(e: Event, ex: EventException) = EventInstance(
        event = e,
        originalDate = ex.originalDate,
        date = ex.date ?: ex.originalDate,
        start = ex.startTime ?: e.startTime,
        end = ex.endTime ?: e.endTime,
        title = ex.title ?: e.title,
        location = ex.location ?: e.location,
        description = ex.description ?: e.description,
        isException = true,
    )

    // ---------------- Tasks per day ----------------

    /** Occurrence dates of a recurring task that appear (after moves/deletions) in [from]..[to], mapped to the shown date. */
    fun taskOccurrencesInRange(task: Task, occurrences: List<TaskOccurrence>, from: LocalDate, to: LocalDate): List<Pair<LocalDate, DayItem>> {
        val rule = RRule.parse(task.rrule) ?: return emptyList()
        val start = task.startDate ?: return emptyList()
        val byDate = occurrences.filter { it.taskId == task.id }.associateBy { it.date }
        val out = mutableListOf<Pair<LocalDate, DayItem>>()
        for (d in Recurrence.occurrences(rule, start, from, to)) {
            val occ = byDate[d]
            if (occ?.status == OccurrenceStatus.DELETED) continue
            val shown = occ?.movedTo ?: d
            if (shown in from..to) out += shown to DayItem(task, d, occ)
        }
        for (occ in byDate.values) {
            val moved = occ.movedTo ?: continue
            if (occ.status == OccurrenceStatus.DELETED) continue
            if (occ.date in from..to) continue
            if (moved !in from..to) continue
            if (!Recurrence.occursOn(rule, start, occ.date)) continue
            out += moved to DayItem(task, occ.date, occ)
        }
        return out
    }

    /** Unordered to-do items for every day in [from]..[to]. Archived tasks are excluded. */
    fun dayItemsInRange(data: AppData, from: LocalDate, to: LocalDate): Map<LocalDate, List<DayItem>> {
        val map = mutableMapOf<LocalDate, MutableList<DayItem>>()
        for (t in data.tasks) {
            if (t.archived) continue
            if (t.isRecurring) {
                if (t.completed) continue // series ended
                for ((date, item) in taskOccurrencesInRange(t, data.occurrences, from, to)) {
                    map.getOrPut(date) { mutableListOf() } += item
                }
            } else {
                val d = t.assignedDate ?: continue
                if (d in from..to) map.getOrPut(d) { mutableListOf() } += DayItem(t, null, null)
            }
        }
        return map
    }

    /** The ordered to-do list of a day. */
    fun dayItems(data: AppData, date: LocalDate): List<DayItem> {
        val items = dayItemsInRange(data, date, date)[date].orEmpty()
        val order = data.dayOrders.filter { it.date == date }.associate { it.itemKey to it.position }
        return items.sortedWith(
            compareBy<DayItem>({ order[it.key] ?: Int.MAX_VALUE }, { it.task.sortOrder }, { it.task.createdAt }, { it.task.id })
        )
    }

    fun reorderedDayOrders(date: LocalDate, keys: List<String>): List<DayOrder> =
        keys.mapIndexed { i, k -> DayOrder(date, k, i) }

    /** Open (non-archived, non-completed) tasks with a deadline inside the range, grouped by deadline. */
    fun deadlinesInRange(tasks: List<Task>, from: LocalDate, to: LocalDate): Map<LocalDate, List<Task>> =
        tasks.filter { it.isOpen && it.deadline != null && it.deadline in from..to }.groupBy { it.deadline!! }

    /** For recurring tasks: the occurrence the user most likely wants to tick off from the overview (today's). */
    fun occurrenceOn(task: Task, occurrences: List<TaskOccurrence>, date: LocalDate): DayItem? =
        taskOccurrencesInRange(task, occurrences, date, date).firstOrNull()?.second

    fun nextOpenOccurrence(task: Task, occurrences: List<TaskOccurrence>, from: LocalDate): LocalDate? {
        val items = taskOccurrencesInRange(task, occurrences, from, from.plusDays(400))
        return items.filter { !it.second.done && !it.second.skipped }.minOfOrNull { it.first }
    }

    // ---------------- Layout helper ----------------

    /** Assigns side-by-side lanes to overlapping events. Returns key -> (lane, laneCount). */
    fun lanes(instances: List<EventInstance>): Map<String, Pair<Int, Int>> {
        val result = mutableMapOf<String, Pair<Int, Int>>()
        val sorted = instances.sortedWith(compareBy({ it.start }, { it.end }))
        var cluster = mutableListOf<Pair<EventInstance, Int>>()
        var clusterEnd = LocalTime.MIN
        val laneEnds = mutableListOf<LocalTime>()

        fun flush() {
            val count = laneEnds.size.coerceAtLeast(1)
            for ((inst, lane) in cluster) result[inst.key] = lane to count
            cluster = mutableListOf()
            laneEnds.clear()
        }

        for (inst in sorted) {
            val end = if (inst.end > inst.start) inst.end else inst.start.plusMinutes(15)
            if (cluster.isNotEmpty() && inst.start >= clusterEnd) flush()
            var lane = laneEnds.indexOfFirst { it <= inst.start }
            if (lane == -1) {
                laneEnds += end
                lane = laneEnds.lastIndex
            } else {
                laneEnds[lane] = end
            }
            cluster += inst to lane
            clusterEnd = if (cluster.size == 1) end else maxOf(clusterEnd, end)
        }
        if (cluster.isNotEmpty()) flush()
        return result
    }
}
