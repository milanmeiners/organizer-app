package com.lifeorganizer.app.domain

import com.lifeorganizer.app.data.AppData
import com.lifeorganizer.app.data.DayOrder
import com.lifeorganizer.app.data.Event
import com.lifeorganizer.app.data.EventException
import com.lifeorganizer.app.data.Goal
import com.lifeorganizer.app.data.GoalStatus
import com.lifeorganizer.app.data.OccurrenceStatus
import com.lifeorganizer.app.data.Task
import com.lifeorganizer.app.data.TaskOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class DomainTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun occ(rule: String, start: String, from: String, to: String) =
        Recurrence.occurrences(RRule.parse(rule)!!, d(start), d(from), d(to)).map { it.toString() }

    // ---------- RRULE parsing ----------

    @Test fun parseAndSerializeRoundTrip() {
        val rules = listOf(
            "FREQ=DAILY", "FREQ=DAILY;INTERVAL=3", "FREQ=WEEKLY;BYDAY=MO,WE,FR",
            "FREQ=WEEKLY;INTERVAL=2;BYDAY=TU", "FREQ=MONTHLY;BYMONTHDAY=15", "FREQ=MONTHLY;BYDAY=-1FR",
            "FREQ=MONTHLY;INTERVAL=3;BYDAY=2MO", "FREQ=YEARLY;COUNT=5", "FREQ=DAILY;UNTIL=20261231",
        )
        for (r in rules) assertEquals(r, RRule.parse(r)!!.toRRuleString())
        assertEquals(RRule.parse("RRULE:FREQ=WEEKLY;BYDAY=+1MO")!!.byDay.first(), WeekdayNum(1, DayOfWeek.MONDAY))
        assertNull(RRule.parse("FREQ=HOURLY"))
        assertNull(RRule.parse("INTERVAL=2"))
        assertNull(RRule.parse("FREQ=DAILY;INTERVAL=0"))
        assertNull(RRule.parse("FREQ=MONTHLY;BYMONTHDAY=0"))
        assertNull(RRule.parse(""))
    }

    // ---------- Expansion ----------

    @Test fun daily() {
        assertEquals(listOf("2026-11-19", "2026-11-20", "2026-11-21"), occ("FREQ=DAILY", "2026-11-19", "2026-11-01", "2026-11-21"))
    }

    @Test fun everyXDays() {
        assertEquals(listOf("2026-11-01", "2026-11-04", "2026-11-07", "2026-11-10"), occ("FREQ=DAILY;INTERVAL=3", "2026-11-01", "2026-11-01", "2026-11-10"))
        // window far after start must give the same phase
        assertEquals(listOf("2027-01-03"), occ("FREQ=DAILY;INTERVAL=3", "2026-11-01", "2027-01-01", "2027-01-04"))
    }

    @Test fun weeklySelectedDays() {
        // 2026-11-16 is a Monday
        assertEquals(
            listOf("2026-11-16", "2026-11-18", "2026-11-20", "2026-11-23"),
            occ("FREQ=WEEKLY;BYDAY=MO,WE,FR", "2026-11-16", "2026-11-16", "2026-11-23"),
        )
        // start on Wednesday: Monday of first week must not be produced
        assertEquals(listOf("2026-11-18", "2026-11-20", "2026-11-23"), occ("FREQ=WEEKLY;BYDAY=MO,WE,FR", "2026-11-18", "2026-11-01", "2026-11-23"))
    }

    @Test fun everyTwoWeeks() {
        assertEquals(
            listOf("2026-11-17", "2026-12-01", "2026-12-15"),
            occ("FREQ=WEEKLY;INTERVAL=2", "2026-11-17", "2026-11-01", "2026-12-20"),
        )
        assertEquals(listOf("2027-03-09"), occ("FREQ=WEEKLY;INTERVAL=2", "2026-11-17", "2027-03-03", "2027-03-15"))
    }

    @Test fun monthlyDayOfMonthSkipsShortMonths() {
        assertEquals(
            listOf("2027-01-31", "2027-03-31", "2027-05-31"),
            occ("FREQ=MONTHLY", "2027-01-31", "2027-01-01", "2027-06-30"),
        )
        assertEquals(
            listOf("2027-01-31", "2027-02-28", "2027-03-31"),
            occ("FREQ=MONTHLY;BYMONTHDAY=-1", "2027-01-31", "2027-01-01", "2027-03-31"),
        )
    }

    @Test fun monthlyNthWeekday() {
        assertEquals(listOf("2026-11-27", "2026-12-25"), occ("FREQ=MONTHLY;BYDAY=-1FR", "2026-11-01", "2026-11-01", "2026-12-31"))
        assertEquals(listOf("2026-11-09", "2027-02-08"), occ("FREQ=MONTHLY;INTERVAL=3;BYDAY=2MO", "2026-11-01", "2026-11-01", "2027-02-28"))
    }

    @Test fun everyXMonths() {
        assertEquals(listOf("2026-01-15", "2026-03-15", "2026-05-15"), occ("FREQ=MONTHLY;INTERVAL=2", "2026-01-15", "2026-01-01", "2026-06-30"))
    }

    @Test fun yearlyLeapDay() {
        assertEquals(listOf("2024-02-29", "2028-02-29"), occ("FREQ=YEARLY", "2024-02-29", "2024-01-01", "2028-12-31"))
    }

    @Test fun countAndUntil() {
        assertEquals(listOf("2026-11-19", "2026-11-20", "2026-11-21"), occ("FREQ=DAILY;COUNT=3", "2026-11-19", "2026-11-01", "2026-12-31"))
        // COUNT is counted from start even if the window starts later
        assertEquals(listOf("2026-11-21"), occ("FREQ=DAILY;COUNT=3", "2026-11-19", "2026-11-21", "2026-12-31"))
        assertEquals(listOf("2026-11-30", "2026-12-01"), occ("FREQ=DAILY;UNTIL=20261201", "2026-11-19", "2026-11-30", "2026-12-31"))
        assertEquals(4, occ("FREQ=WEEKLY;BYDAY=MO,TH;COUNT=4", "2026-11-16", "2026-01-01", "2027-12-31").size)
    }

    @Test fun describe() {
        assertEquals("Every weekday", Recurrence.describe("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"))
        assertEquals("Monthly on the last Friday", Recurrence.describe("FREQ=MONTHLY;BYDAY=-1FR"))
        assertEquals("Every 3 days, 10 times", Recurrence.describe("FREQ=DAILY;INTERVAL=3;COUNT=10"))
        assertEquals("Does not repeat", Recurrence.describe(null as String?))
    }

    // ---------- Events with exceptions ----------

    @Test fun eventExceptions() {
        val ev = Event(id = 1, title = "Gym", date = d("2026-11-16"), startTime = LocalTime.of(18, 0), endTime = LocalTime.of(19, 0), rrule = "FREQ=WEEKLY;BYDAY=MO,TH")
        val exs = listOf(
            EventException(1, d("2026-11-19"), cancelled = true),
            EventException(1, d("2026-11-23"), date = d("2026-11-24"), startTime = LocalTime.of(7, 0), endTime = LocalTime.of(8, 0)),
            EventException(1, d("2026-11-30"), date = d("2026-12-05")),
        )
        val list = Planner.eventsInRange(listOf(ev), exs, d("2026-11-16"), d("2026-11-30"))
        assertEquals(listOf("2026-11-16", "2026-11-24", "2026-11-26"), list.map { it.date.toString() })
        assertEquals(LocalTime.of(7, 0), list[1].start)
        // occurrence moved from outside into the window
        val dec = Planner.eventsInRange(listOf(ev), exs, d("2026-12-05"), d("2026-12-05"))
        assertEquals(1, dec.size)
        assertEquals(d("2026-11-30"), dec[0].originalDate)
    }

    // ---------- Recurring tasks ----------

    @Test fun recurringTaskOccurrencesIndependent() {
        val t = Task(id = 5, title = "Exercise", startDate = d("2026-11-16"), rrule = "FREQ=DAILY")
        val occs = listOf(
            TaskOccurrence(5, d("2026-11-17"), OccurrenceStatus.DONE),
            TaskOccurrence(5, d("2026-11-18"), OccurrenceStatus.DELETED),
            TaskOccurrence(5, d("2026-11-19"), OccurrenceStatus.SKIPPED),
            TaskOccurrence(5, d("2026-11-20"), movedTo = d("2026-11-22")),
        )
        val data = AppData(tasks = listOf(t), occurrences = occs)
        val map = Planner.dayItemsInRange(data, d("2026-11-16"), d("2026-11-22"))
        assertTrue(map[d("2026-11-17")]!!.single().done)
        assertNull(map[d("2026-11-18")])
        assertTrue(map[d("2026-11-19")]!!.single().skipped)
        assertNull(map[d("2026-11-20")])
        assertEquals(2, map[d("2026-11-22")]!!.size)
        assertFalse(map[d("2026-11-21")]!!.single().done)
        assertEquals(d("2026-11-16"), Planner.nextOpenOccurrence(t, occs, d("2026-11-16")))
        assertEquals(d("2026-11-21"), Planner.nextOpenOccurrence(t, occs, d("2026-11-17")))
    }

    @Test fun dayOrderAndPersistence() {
        val a = Task(id = 1, title = "A", assignedDate = d("2026-11-10"))
        val b = Task(id = 2, title = "B", assignedDate = d("2026-11-10"))
        val data = AppData(tasks = listOf(a, b), dayOrders = listOf(DayOrder(d("2026-11-10"), "t:2", 0), DayOrder(d("2026-11-10"), "t:1", 1)))
        assertEquals(listOf("B", "A"), Planner.dayItems(data, d("2026-11-10")).map { it.title })
        // Unfinished task with a past assigned date stays in "All open"
        assertTrue(TaskQueries.inSection(a, TaskSection.ALL_OPEN, d("2026-11-19")))
    }

    // ---------- Task sections ----------

    @Test fun sections() {
        val today = d("2026-11-19")
        val overdue = Task(id = 1, title = "o", deadline = d("2026-11-18"))
        val upcoming = Task(id = 2, title = "u", deadline = d("2026-11-30"))
        val none = Task(id = 3, title = "n")
        val done = Task(id = 4, title = "d", completed = true, deadline = d("2026-11-01"))
        val lt = Task(id = 5, title = "l", trackProgress = true, progress = 40)
        val all = listOf(overdue, upcoming, none, done, lt)
        assertEquals(listOf(1L), TaskQueries.query(all, TaskSection.OVERDUE, TaskFilter(), TaskSort.DEADLINE, today).map { it.id })
        assertEquals(listOf(2L), TaskQueries.query(all, TaskSection.UPCOMING, TaskFilter(), TaskSort.DEADLINE, today).map { it.id })
        assertEquals(setOf(3L, 5L), TaskQueries.query(all, TaskSection.NO_DEADLINE, TaskFilter(), TaskSort.DEADLINE, today).map { it.id }.toSet())
        assertEquals(listOf(4L), TaskQueries.query(all, TaskSection.COMPLETED, TaskFilter(), TaskSort.DEADLINE, today).map { it.id })
        assertEquals(listOf(5L), TaskQueries.query(all, TaskSection.LONG_TERM, TaskFilter(), TaskSort.DEADLINE, today).map { it.id })
        assertEquals(DeadlineState.OVERDUE, TaskQueries.deadlineState(overdue, today))
        assertEquals(DeadlineState.SOON, TaskQueries.deadlineState(Task(title = "s", deadline = d("2026-11-21")), today))
        assertEquals(listOf(1L, 2L, 5L, 3L), TaskQueries.query(all, TaskSection.ALL_OPEN, TaskFilter(), TaskSort.DEADLINE, today).map { it.id })
    }

    // ---------- Goals ----------

    @Test fun goalProgressAndCycles() {
        val goals = listOf(
            Goal(id = 1, title = "Find a new job"),
            Goal(id = 2, title = "Professional development", parentId = 1),
            Goal(id = 3, title = "Building AI", parentId = 2, manualProgress = 100),
            Goal(id = 4, title = "Learn tools", parentId = 2, manualProgress = 50),
            Goal(id = 5, title = "Build app", parentId = 2, manualProgress = 0),
            Goal(id = 6, title = "Apply", parentId = 1, status = GoalStatus.COMPLETED),
            Goal(id = 7, title = "Archived one", parentId = 1, status = GoalStatus.ARCHIVED, manualProgress = 0),
        )
        val tree = GoalTree(goals)
        assertEquals(GoalProgress(50, true), tree.progress(2))
        assertEquals(GoalProgress(100, false), tree.progress(6))
        assertEquals(GoalProgress(75, true), tree.progress(1)) // (50 + 100) / 2, archived ignored
        assertTrue(tree.wouldCreateCycle(1, 3))
        assertTrue(tree.wouldCreateCycle(2, 2))
        assertFalse(tree.wouldCreateCycle(3, 6))
        assertFalse(tree.wouldCreateCycle(3, null))
        assertEquals(setOf(2L, 3L, 4L, 5L, 6L, 7L), tree.descendants(1))
        assertEquals(listOf(1L, 2L, 4L), tree.path(4).map { it.id })
        val flat = tree.flatten(setOf(1L, 2L), setOf(GoalStatus.ACTIVE, GoalStatus.COMPLETED))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), flat.map { it.goal.id })
        assertEquals(2, flat.first { it.goal.id == 3L }.depth)
        assertNotNull(tree.get(7))
    }

    @Test fun lanes() {
        val ev = { id: Long, s: Int, e: Int ->
            EventInstance(Event(id = id, title = "$id", date = d("2026-11-19"), startTime = LocalTime.of(s, 0), endTime = LocalTime.of(e, 0)),
                d("2026-11-19"), d("2026-11-19"), LocalTime.of(s, 0), LocalTime.of(e, 0), "$id", "", "", false)
        }
        val l = Planner.lanes(listOf(ev(1, 9, 11), ev(2, 10, 12), ev(3, 13, 14)))
        assertEquals(0 to 2, l["e:1:2026-11-19"])
        assertEquals(1 to 2, l["e:2:2026-11-19"])
        assertEquals(0 to 1, l["e:3:2026-11-19"])
    }
}
