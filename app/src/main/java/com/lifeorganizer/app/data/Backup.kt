package com.lifeorganizer.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** JSON export / import of all data. The format is versioned so later app versions can still read old backups. */
object Backup {
    const val FORMAT_VERSION = 1

    fun toJson(d: AppData): String {
        val root = JSONObject()
        root.put("app", "LifeOrganizer")
        root.put("formatVersion", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("goals", JSONArray(d.goals.map { g ->
            JSONObject()
                .put("id", g.id).put("title", g.title).put("description", g.description)
                .putOpt("parentId", g.parentId).putOpt("deadline", g.deadline?.toString())
                .put("priority", g.priority).put("status", g.status.name)
                .putOpt("manualProgress", g.manualProgress).put("notes", g.notes)
                .put("sortOrder", g.sortOrder).put("createdAt", g.createdAt)
        }))
        root.put("tasks", JSONArray(d.tasks.map { t ->
            JSONObject()
                .put("id", t.id).put("title", t.title).put("description", t.description)
                .put("completed", t.completed).putOpt("completedAt", t.completedAt)
                .put("trackProgress", t.trackProgress).put("progress", t.progress)
                .put("priority", t.priority).put("category", t.category)
                .putOpt("startDate", t.startDate?.toString()).putOpt("deadline", t.deadline?.toString())
                .putOpt("assignedDate", t.assignedDate?.toString()).putOpt("rrule", t.rrule)
                .putOpt("goalId", t.goalId).put("notes", t.notes).put("workFrequency", t.workFrequency)
                .put("archived", t.archived).put("sortOrder", t.sortOrder).put("createdAt", t.createdAt)
        }))
        root.put("taskOccurrences", JSONArray(d.occurrences.map { o ->
            JSONObject()
                .put("taskId", o.taskId).put("date", o.date.toString()).put("status", o.status.name)
                .putOpt("titleOverride", o.titleOverride).putOpt("notesOverride", o.notesOverride)
                .putOpt("movedTo", o.movedTo?.toString())
        }))
        root.put("events", JSONArray(d.events.map { e ->
            JSONObject()
                .put("id", e.id).put("title", e.title).put("description", e.description)
                .put("date", e.date.toString()).put("startTime", e.startTime.toString())
                .put("endTime", e.endTime.toString()).put("location", e.location)
                .putOpt("rrule", e.rrule).put("createdAt", e.createdAt)
        }))
        root.put("eventExceptions", JSONArray(d.exceptions.map { x ->
            JSONObject()
                .put("eventId", x.eventId).put("originalDate", x.originalDate.toString())
                .put("cancelled", x.cancelled).putOpt("date", x.date?.toString())
                .putOpt("startTime", x.startTime?.toString()).putOpt("endTime", x.endTime?.toString())
                .putOpt("title", x.title).putOpt("description", x.description).putOpt("location", x.location)
        }))
        root.put("dayOrder", JSONArray(d.dayOrders.map { o ->
            JSONObject().put("date", o.date.toString()).put("itemKey", o.itemKey).put("position", o.position)
        }))
        return root.toString(2)
    }

    /** Throws IllegalArgumentException with a readable message if the file is not a valid backup. */
    fun fromJson(json: String): AppData {
        val root = try { JSONObject(json) } catch (e: Exception) { throw IllegalArgumentException("Not a valid backup file") }
        if (root.optString("app") != "LifeOrganizer") throw IllegalArgumentException("Not a Life Organizer backup")
        val v = root.optInt("formatVersion", 0)
        if (v < 1 || v > FORMAT_VERSION) throw IllegalArgumentException("Unsupported backup version $v")
        try {
            return AppData(
                goals = root.optJSONArray("goals").objects().map { o ->
                    Goal(
                        id = o.getLong("id"), title = o.getString("title"), description = o.optString("description"),
                        parentId = o.optLongOrNull("parentId"), deadline = o.optDate("deadline"),
                        priority = o.optInt("priority"), status = GoalStatus.valueOf(o.optString("status", "ACTIVE")),
                        manualProgress = o.optIntOrNull("manualProgress"), notes = o.optString("notes"),
                        sortOrder = o.optInt("sortOrder"), createdAt = o.optLong("createdAt"),
                    )
                },
                tasks = root.optJSONArray("tasks").objects().map { o ->
                    Task(
                        id = o.getLong("id"), title = o.getString("title"), description = o.optString("description"),
                        completed = o.optBoolean("completed"), completedAt = o.optLongOrNull("completedAt"),
                        trackProgress = o.optBoolean("trackProgress"), progress = o.optInt("progress"),
                        priority = o.optInt("priority"), category = o.optString("category"),
                        startDate = o.optDate("startDate"), deadline = o.optDate("deadline"),
                        assignedDate = o.optDate("assignedDate"), rrule = o.optStringOrNull("rrule"),
                        goalId = o.optLongOrNull("goalId"), notes = o.optString("notes"),
                        workFrequency = o.optString("workFrequency"), archived = o.optBoolean("archived"),
                        sortOrder = o.optInt("sortOrder"), createdAt = o.optLong("createdAt"),
                    )
                },
                occurrences = root.optJSONArray("taskOccurrences").objects().map { o ->
                    TaskOccurrence(
                        taskId = o.getLong("taskId"), date = LocalDate.parse(o.getString("date")),
                        status = OccurrenceStatus.valueOf(o.optString("status", "OPEN")),
                        titleOverride = o.optStringOrNull("titleOverride"), notesOverride = o.optStringOrNull("notesOverride"),
                        movedTo = o.optDate("movedTo"),
                    )
                },
                events = root.optJSONArray("events").objects().map { o ->
                    Event(
                        id = o.getLong("id"), title = o.getString("title"), description = o.optString("description"),
                        date = LocalDate.parse(o.getString("date")), startTime = LocalTime.parse(o.getString("startTime")),
                        endTime = LocalTime.parse(o.getString("endTime")), location = o.optString("location"),
                        rrule = o.optStringOrNull("rrule"), createdAt = o.optLong("createdAt"),
                    )
                },
                exceptions = root.optJSONArray("eventExceptions").objects().map { o ->
                    EventException(
                        eventId = o.getLong("eventId"), originalDate = LocalDate.parse(o.getString("originalDate")),
                        cancelled = o.optBoolean("cancelled"), date = o.optDate("date"),
                        startTime = o.optStringOrNull("startTime")?.let { LocalTime.parse(it) },
                        endTime = o.optStringOrNull("endTime")?.let { LocalTime.parse(it) },
                        title = o.optStringOrNull("title"), description = o.optStringOrNull("description"),
                        location = o.optStringOrNull("location"),
                    )
                },
                dayOrders = root.optJSONArray("dayOrder").objects().map { o ->
                    DayOrder(LocalDate.parse(o.getString("date")), o.getString("itemKey"), o.getInt("position"))
                },
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("Backup file is damaged: ${e.message}")
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else getInt(key)

    private fun JSONObject.optDate(key: String): LocalDate? = optStringOrNull(key)?.let { LocalDate.parse(it) }
}
