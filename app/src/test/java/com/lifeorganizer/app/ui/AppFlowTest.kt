package com.lifeorganizer.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.lifeorganizer.app.MainActivity
import com.lifeorganizer.app.data.AppDatabase
import com.lifeorganizer.app.data.Backup
import com.lifeorganizer.app.data.ChildGoalHandling
import com.lifeorganizer.app.data.Event
import com.lifeorganizer.app.data.Goal
import com.lifeorganizer.app.data.LinkedTaskHandling
import com.lifeorganizer.app.data.OccurrenceStatus
import com.lifeorganizer.app.data.Priority
import com.lifeorganizer.app.data.Repository
import com.lifeorganizer.app.data.Task
import com.lifeorganizer.app.domain.GoalTree
import com.lifeorganizer.app.domain.Planner
import com.lifeorganizer.app.print.PdfPrinter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val today = LocalDate.now()

    @After fun tearDown() {
        AppDatabase.resetForTests()
        ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase("organizer.db")
    }
    private val repo: Repository get() = Repository(AppDatabase.get(ApplicationProvider.getApplicationContext()))

    private fun save(name: String, bmp: Bitmap) = runCatching {
        val dir = File("build/screens").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        // Screenshots need Robolectric's native graphics; skipped silently when unavailable.
        runCatching {
            val view = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bmp))
            save(name, bmp)
        }.onFailure { println("SHOT FAIL $name: $it") }
    }

    private fun waitForText(text: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun seed(): Map<String, Long> = runBlocking {
        val r = repo
        val job = r.saveGoal(Goal(title = "Find a new job", priority = Priority.HIGH, deadline = today.plusMonths(4)))
        val dev = r.saveGoal(Goal(title = "Complete professional development", parentId = job))
        val ai = r.saveGoal(Goal(title = "Complete the Building AI course", parentId = dev, manualProgress = 40))
        r.saveGoal(Goal(title = "Learn programming and AI tools", parentId = dev, manualProgress = 50))
        r.saveGoal(Goal(title = "Build a personal app", parentId = dev, manualProgress = 90))
        r.saveGoal(Goal(title = "Research potential employers", parentId = job, manualProgress = 20))
        r.saveGoal(Goal(title = "Run a half marathon", manualProgress = 10))

        r.saveEvent(Event(title = "Job interview", date = today.plusDays(1), startTime = LocalTime.of(10, 0), endTime = LocalTime.of(11, 0), location = "Acme GmbH"))
        r.saveEvent(Event(title = "Doctor", date = today, startTime = LocalTime.of(14, 0), endTime = LocalTime.of(14, 45)))
        r.saveEvent(Event(title = "Team call", date = today, startTime = LocalTime.of(14, 30), endTime = LocalTime.of(15, 30)))
        r.saveEvent(Event(title = "Gym", date = today.minusDays(14), startTime = LocalTime.of(18, 0), endTime = LocalTime.of(19, 30), rrule = "FREQ=WEEKLY;BYDAY=MO,TH"))

        val course = r.saveTask(Task(title = "Building AI course", trackProgress = true, progress = 40, deadline = today.plusDays(11), goalId = ai, workFrequency = "3× per week", category = "Learning", assignedDate = today))
        val exercise = r.saveTask(Task(title = "Exercise", startDate = today.minusDays(3), rrule = "FREQ=DAILY", category = "Health"))
        r.saveTask(Task(title = "Shopping", assignedDate = today, priority = Priority.MEDIUM, category = "Household"))
        r.saveTask(Task(title = "Tax return", deadline = today.minusDays(2), priority = Priority.HIGH, category = "Admin"))
        r.saveTask(Task(title = "Read 'Deep Work'", trackProgress = true, progress = 65))
        r.saveTask(Task(title = "Update CV", deadline = today.plusDays(2), goalId = job, assignedDate = today.plusDays(1)))
        mapOf("job" to job, "dev" to dev, "course" to course, "exercise" to exercise)
    }

    @Test
    fun fullTour() {
        val ids = seed()
        waitForText("Doctor")
        shot("01_month")

        rule.onNodeWithText("Week").performClick(); shot("02_week")
        rule.onNodeWithText("Year").performClick(); shot("03_year")
        rule.onNodeWithText("Day").performClick()
        rule.onNodeWithText("Today").performClick()
        waitForText("Exercise")
        shot("04_day")

        // Tick off today's occurrence of the recurring task: only today's occurrence is completed.
        rule.onAllNodesWithText("Exercise").onFirst().assertExists()
        val exerciseId = ids.getValue("exercise")
        runBlocking {
            val data = repo.snapshot()
            val item = Planner.dayItems(data, today).first { it.task.id == exerciseId }
            repo.setOccurrenceStatus(item.task, today, item.occurrence, OccurrenceStatus.DONE)
            val after = repo.snapshot()
            val todayItem = Planner.dayItems(after, today).first { it.task.id == exerciseId }
            val tomorrowItem = Planner.dayItems(after, today.plusDays(1)).first { it.task.id == exerciseId }
            assertTrue(todayItem.done)
            assertTrue(!tomorrowItem.done)
        }
        shot("05_day_after_tick")
        rule.onNodeWithText("Shopping").performScrollTo()
        shot("05b_day_todo")

        rule.onNodeWithText("Tasks").performClick()
        waitForText("Tax return")
        shot("06_tasks")
        rule.onNodeWithText("Overdue 1").performClick(); shot("07_tasks_overdue")
        rule.onNodeWithText("All open", substring = true).performClick()
        rule.onNodeWithText("Building AI course").performClick()
        waitForText("Edit task")
        shot("08_task_editor")
        rule.onNodeWithContentDescription("Close").performClick()

        rule.onNodeWithText("Goals").performClick()
        rule.onNodeWithText("Expand all").performClick()
        waitForText("Research potential employers")
        shot("09_goals")
        rule.onNodeWithText("Complete professional development").performClick()
        waitForText("SUBGOALS")
        shot("10_goal_detail")
        rule.onNodeWithContentDescription("Back").performClick()

        // Create a task through the UI with the + button.
        rule.onNodeWithContentDescription("Create").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Task").performClick()
        waitForText("New task")
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Call the landlord")
        shot("11_new_task")
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Tasks").performClick()
        waitForText("Call the landlord")

        // Create an event through the UI.
        rule.onNodeWithContentDescription("Create").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Event").performClick()
        waitForText("New event")
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Dentist")
        shot("12_new_event")
        rule.onNodeWithText("Save").performClick()
        runBlocking { assertTrue(repo.snapshot().events.any { it.title == "Dentist" }) }

        // Create a goal through the UI.
        rule.onNodeWithContentDescription("Create").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Goal").performClick()
        waitForText("New goal")
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Learn Spanish")
        rule.onNodeWithText("Save").performClick()
        waitForText("Learn Spanish")
        shot("13_new_goal_detail")
    }

    @Test
    fun goalRulesBackupAndPrint() = runBlocking {
        val ids = seed()
        val r = repo
        val job = ids.getValue("job")
        val dev = ids.getValue("dev")

        // Circular parent relation is rejected.
        val devGoal = r.snapshot().goals.first { it.id == dev }
        val child = r.snapshot().goals.first { it.parentId == dev }
        var rejected = false
        try { r.saveGoal(devGoal.copy(parentId = child.id)) } catch (e: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)

        // Progress of "professional development" = avg(40, 50, 90) = 60; job = avg(60, 20) = 40.
        val tree = GoalTree(r.snapshot().goals)
        assertEquals(60, tree.progress(dev).percent)
        assertEquals(40, tree.progress(job).percent)

        // Backup round trip.
        val before = r.snapshot()
        val json = Backup.toJson(before)
        r.replaceAll(Backup.fromJson(json))
        val after = r.snapshot()
        assertEquals(before.tasks.sortedBy { it.id }, after.tasks.sortedBy { it.id })
        assertEquals(before.goals.sortedBy { it.id }, after.goals.sortedBy { it.id })
        assertEquals(before.events.sortedBy { it.id }, after.events.sortedBy { it.id })

        // Delete "professional development", keep children (move up) and unlink tasks.
        r.deleteGoal(dev, ChildGoalHandling.MOVE_UP, LinkedTaskHandling.UNLINK)
        val s = r.snapshot()
        assertNull(s.goals.find { it.id == dev })
        assertTrue(s.goals.filter { it.title.startsWith("Complete the Building") || it.title.startsWith("Learn programming") }.all { it.parentId == job })
        assertEquals(6, s.tasks.size) // tasks preserved

        // Delete job with whole subtree and its linked tasks.
        r.deleteGoal(job, ChildGoalHandling.DELETE, LinkedTaskHandling.DELETE)
        val s2 = r.snapshot()
        assertEquals(1, s2.goals.size) // only "Run a half marathon"
        assertTrue(s2.tasks.none { it.title == "Update CV" || it.title == "Building AI course" })

        // PDF pages render.
        val data = Backup.fromJson(json)
        PdfPrinter.previewBitmaps(data, today, week = false).forEachIndexed { i, b -> save("20_pdf_day_$i", b) }
        PdfPrinter.previewBitmaps(data, today, week = true).forEachIndexed { i, b -> save("21_pdf_week_$i", b) }
    }
}
