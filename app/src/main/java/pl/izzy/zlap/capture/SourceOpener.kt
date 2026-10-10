package pl.izzy.zlap.capture

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import pl.izzy.zlap.data.Task

/** Otwiera rozmowę, z której złapano zadanie – najdokładniejszym dostępnym sposobem. */
object SourceOpener {

    fun canOpen(task: Task): Boolean = task.sourcePackage != null || task.link != null

    fun open(context: Context, task: Task) {
        // 1. Oryginalne powiadomienie: otwiera dokładnie ten czat (do restartu telefonu)
        RecentStore.intentFor(task.id)?.let { if (send(context, it)) return }

        // 2. Trwały link z numeru telefonu
        task.link?.let { link ->
            val intent = if (link.startsWith("smsto:")) {
                Intent(Intent.ACTION_SENDTO, Uri.parse(link))
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(task.sourcePackage)
            }
            if (start(context, intent)) return
        }

        // 3. Sama aplikacja
        task.sourcePackage
            ?.let(context.packageManager::getLaunchIntentForPackage)
            ?.let { start(context, it) }
    }

    private fun send(context: Context, intent: PendingIntent): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val options = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            intent.send(context, 0, null, null, null, null, options.toBundle())
        } else {
            intent.send()
        }
        true
    } catch (e: PendingIntent.CanceledException) {
        false
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
