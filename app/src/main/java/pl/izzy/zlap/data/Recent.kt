package pl.izzy.zlap.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Ostatnie powiadomienie z danej rozmowy – zapisane, żeby przetrwało zamknięcie aplikacji przez MIUI. */
@Entity(tableName = "recent")
data class RecentEntity(
    @PrimaryKey val key: String,
    val packageName: String,
    val appName: String,
    val sender: String?,
    val text: String?,
    val postedAt: Long,
    val link: String?,
)

@Dao
interface RecentDao {
    @Query("SELECT * FROM recent WHERE postedAt >= :since ORDER BY postedAt DESC LIMIT :limit")
    fun observe(since: Long, limit: Int): Flow<List<RecentEntity>>

    @Upsert
    suspend fun upsert(entity: RecentEntity)

    @Query("DELETE FROM recent WHERE postedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
