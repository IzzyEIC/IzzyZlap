package pl.izzy.zlap.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Task::class], version = 1, exportSchema = false)
abstract class ZlapDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        fun create(context: Context): ZlapDatabase =
            Room.databaseBuilder(context, ZlapDatabase::class.java, "zlap.db").build()
    }
}
