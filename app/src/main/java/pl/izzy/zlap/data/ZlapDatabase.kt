package pl.izzy.zlap.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Task::class, RecentEntity::class], version = 3, exportSchema = false)
abstract class ZlapDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun recentDao(): RecentDao

    companion object {
        fun create(context: Context): ZlapDatabase =
            Room.databaseBuilder(context, ZlapDatabase::class.java, "zlap.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN sender TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN link TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recent` (`key` TEXT NOT NULL, `packageName` TEXT NOT NULL, " +
                        "`appName` TEXT NOT NULL, `sender` TEXT, `text` TEXT, `postedAt` INTEGER NOT NULL, " +
                        "`link` TEXT, PRIMARY KEY(`key`))"
                )
            }
        }
    }
}
