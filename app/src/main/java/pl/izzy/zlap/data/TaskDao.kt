package pl.izzy.zlap.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE done = 0 ORDER BY dueEpochDay, createdAt")
    fun observeOpen(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE done = 0 AND dueEpochDay <= :day ORDER BY dueEpochDay, createdAt")
    suspend fun dueOnOrBefore(day: Long): List<Task>

    @Query("SELECT MIN(dueEpochDay) FROM tasks WHERE done = 0")
    suspend fun earliestOpenDay(): Long?

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Upsert
    suspend fun upsert(task: Task)

    @Delete
    suspend fun delete(task: Task)
}
