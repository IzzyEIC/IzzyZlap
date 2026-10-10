package pl.izzy.zlap.capture

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import pl.izzy.zlap.R
import pl.izzy.zlap.app
import pl.izzy.zlap.reminders.Notifier
import kotlin.math.abs

/** Pływająca bańka nad innymi aplikacjami. Stuknięcie = złap, przeciągnięcie = przesuń. */
class BubbleService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        val notification = Notifier.bubbleNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notifier.BUBBLE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.BUBBLE_NOTIFICATION_ID, notification)
        }
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (bubble == null) showBubble()
        return START_STICKY
    }

    override fun onDestroy() {
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    private fun showBubble() {
        if (!Settings.canDrawOverlays(this)) return
        val settings = app.settings
        val size = (56 * resources.displayMetrics.density).toInt()

        val view = ImageView(this).apply {
            setImageResource(R.drawable.ic_stat_zlap)
            setColorFilter(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFE8590C.toInt())
            }
            val padding = size / 4
            setPadding(padding, padding, padding, padding)
            alpha = 0.9f
            contentDescription = "Złap"
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = settings.bubbleX
            y = settings.bubbleY
        }

        view.setOnTouchListener(DragOrTap(params, size))
        windowManager.addView(view, params)
        bubble = view
    }

    private inner class DragOrTap(
        private val params: WindowManager.LayoutParams,
        private val size: Int,
    ) : View.OnTouchListener {
        private val slop = ViewConfiguration.get(this@BubbleService).scaledTouchSlop
        private var startX = 0
        private var startY = 0
        private var downRawX = 0f
        private var downRawY = 0f
        private var moved = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        windowManager.updateViewLayout(view, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        // Przyklej do najbliższej krawędzi, żeby nie zasłaniała środka ekranu
                        val width = resources.displayMetrics.widthPixels
                        params.x = if (params.x + size / 2 < width / 2) 0 else width - size
                        params.y = params.y.coerceAtLeast(0)
                        windowManager.updateViewLayout(view, params)
                        app.settings.bubbleX = params.x
                        app.settings.bubbleY = params.y
                    } else {
                        startActivity(CaptureActivity.intent(this@BubbleService))
                    }
                }
            }
            return true
        }
    }

    companion object {
        /** Włącza lub wyłącza bańkę zgodnie z ustawieniem i uprawnieniem. */
        fun sync(context: Context) {
            val intent = Intent(context, BubbleService::class.java)
            if (context.app.settings.bubbleEnabled && Settings.canDrawOverlays(context)) {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: IllegalStateException) {
                    // System nie pozwolił wystartować w tle – spróbujemy przy następnym otwarciu aplikacji
                }
            } else {
                context.stopService(intent)
            }
        }
    }
}
