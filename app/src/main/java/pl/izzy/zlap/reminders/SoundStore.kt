package pl.izzy.zlap.reminders

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Własny plik dźwięku kopiujemy do systemowego folderu Notifications/Zlap.
 * Dzięki temu system odtwarza go jak każdy inny dźwięk powiadomienia,
 * także po restarcie, bez dodatkowych uprawnień.
 */
object SoundStore {

    data class Imported(val uri: Uri, val name: String, val durationSec: Long?)

    suspend fun import(context: Context, source: Uri): Imported? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val fileName = resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?: "Zlap.mp3"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, resolver.getType(source) ?: "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_NOTIFICATIONS + "/Zlap")
            put(MediaStore.Audio.AudioColumns.IS_NOTIFICATION, 1)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext null
        try {
            resolver.openInputStream(source)!!.use { input ->
                resolver.openOutputStream(target)!!.use { output -> input.copyTo(output) }
            }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            return@withContext null
        }
        Imported(target, fileName.substringBeforeLast('.'), durationSec(context, target))
    }

    /** Usuwa wcześniej zaimportowany plik, żeby nie zostawiać śmieci w Notifications/Zlap. */
    suspend fun deleteOwned(context: Context, uri: String) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(Uri.parse(uri), null, null)
        } catch (e: Exception) {
            // Plik mógł zostać już usunięty ręcznie – nic nie szkodzi
        }
    }

    private fun durationSec(context: Context, uri: Uri): Long? = try {
        MediaMetadataRetriever().run {
            setDataSource(context, uri)
            val ms = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            release()
            ms?.div(1000)
        }
    } catch (e: Exception) {
        null
    }
}
