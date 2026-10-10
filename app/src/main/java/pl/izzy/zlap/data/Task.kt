package pl.izzy.zlap.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val detail: String? = null,
    /** Aplikacja, z której złapano zadanie (np. com.whatsapp) – do szybkiego powrotu. */
    val sourcePackage: String? = null,
    val sourceApp: String? = null,
    val dueEpochDay: Long,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val doneAt: Long? = null,
)
