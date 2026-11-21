package com.lifeorganizer.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter fun dateToString(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun stringToDate(s: String?): LocalDate? = s?.let { LocalDate.parse(it) }
    @TypeConverter fun timeToString(t: LocalTime?): String? = t?.toString()
    @TypeConverter fun stringToTime(s: String?): LocalTime? = s?.let { LocalTime.parse(it) }
}

@Dao
interface AppDao {
    // ----- Observing -----
    @Query("SELECT * FROM tasks") fun observeTasks(): Flow<List<Task>>
    @Query("SELECT * FROM task_occurrences") fun observeOccurrences(): Flow<List<TaskOccurrence>>
    @Query("SELECT * FROM events") fun observeEvents(): Flow<List<Event>>
    @Query("SELECT * FROM event_exceptions") fun observeExceptions(): Flow<List<EventException>>
    @Query("SELECT * FROM goals") fun observeGoals(): Flow<List<Goal>>
    @Query("SELECT * FROM day_order") fun observeDayOrders(): Flow<List<DayOrder>>

    // ----- One-shot reads (backup) -----
    @Query("SELECT * FROM tasks") suspend fun allTasks(): List<Task>
    @Query("SELECT * FROM task_occurrences") suspend fun allOccurrences(): List<TaskOccurrence>
    @Query("SELECT * FROM events") suspend fun allEvents(): List<Event>
    @Query("SELECT * FROM event_exceptions") suspend fun allExceptions(): List<EventException>
    @Query("SELECT * FROM goals") suspend fun allGoals(): List<Goal>
    @Query("SELECT * FROM day_order") suspend fun allDayOrders(): List<DayOrder>
    @Query("SELECT * FROM goals WHERE id = :id") suspend fun goal(id: Long): Goal?
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun task(id: Long): Task?

    // ----- Writes -----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveTask(t: Task): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveTasks(t: List<Task>)
    @Delete suspend fun deleteTask(t: Task)
    @Query("DELETE FROM tasks WHERE id IN (:ids)") suspend fun deleteTasksByIds(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveOccurrence(o: TaskOccurrence)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveOccurrences(o: List<TaskOccurrence>)
    @Query("DELETE FROM task_occurrences WHERE taskId = :taskId") suspend fun deleteOccurrencesOf(taskId: Long)
    @Query("DELETE FROM task_occurrences WHERE taskId IN (:ids)") suspend fun deleteOccurrencesOfTasks(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveEvent(e: Event): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveEvents(e: List<Event>)
    @Query("DELETE FROM events WHERE id = :id") suspend fun deleteEvent(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveException(e: EventException)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveExceptions(e: List<EventException>)
    @Query("DELETE FROM event_exceptions WHERE eventId = :eventId") suspend fun deleteExceptionsOf(eventId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveGoal(g: Goal): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveGoals(g: List<Goal>)
    @Query("DELETE FROM goals WHERE id IN (:ids)") suspend fun deleteGoalsByIds(ids: List<Long>)
    @Query("UPDATE goals SET parentId = :newParent WHERE parentId = :oldParent") suspend fun reparentChildren(oldParent: Long, newParent: Long?)
    @Query("UPDATE tasks SET goalId = NULL WHERE goalId IN (:goalIds)") suspend fun unlinkTasksFromGoals(goalIds: List<Long>)
    @Query("SELECT id FROM tasks WHERE goalId IN (:goalIds)") suspend fun taskIdsForGoals(goalIds: List<Long>): List<Long>

    @Query("DELETE FROM day_order WHERE date = :date") suspend fun clearDayOrder(date: LocalDate)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveDayOrders(list: List<DayOrder>)
    @Query("DELETE FROM day_order WHERE itemKey = :key OR itemKey LIKE :prefix") suspend fun deleteDayOrderKeys(key: String, prefix: String)

    // ----- Wipe (for import) -----
    @Query("DELETE FROM tasks") suspend fun wipeTasks()
    @Query("DELETE FROM task_occurrences") suspend fun wipeOccurrences()
    @Query("DELETE FROM events") suspend fun wipeEvents()
    @Query("DELETE FROM event_exceptions") suspend fun wipeExceptions()
    @Query("DELETE FROM goals") suspend fun wipeGoals()
    @Query("DELETE FROM day_order") suspend fun wipeDayOrders()
}

@Database(
    entities = [Task::class, TaskOccurrence::class, Event::class, EventException::class, Goal::class, DayOrder::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "organizer.db")
                .build().also { instance = it }
        }

        /** Only for tests: forget the singleton so the next test gets a fresh database. */
        fun resetForTests() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
