package app.songlane.playback

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.songlane.MainActivity
import app.songlane.R

class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, "Your next favorite is waiting", "Songlane", false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION) {
            val pending = goAsync()
            runPlaybackAction(context, intent.getStringExtra("control") ?: "toggle") {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION = "app.songlane.WIDGET_CONTROL"

        fun update(context: Context, title: String, artist: String, playing: Boolean) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.player_widget)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setImageViewResource(
                R.id.widget_toggle,
                if (playing) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
            )
            views.setContentDescription(R.id.widget_toggle, if (playing) "Pause" else "Play")
            val open =
                PendingIntent.getActivity(
                    context,
                    10,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            views.setOnClickPendingIntent(R.id.widget_text, open)
            listOf(
                    R.id.widget_previous to "previous",
                    R.id.widget_toggle to "toggle",
                    R.id.widget_next to "next",
                )
                .forEachIndexed { i, (view, control) ->
                    views.setOnClickPendingIntent(
                        view,
                        PendingIntent.getBroadcast(
                            context,
                            i,
                            Intent(context, PlayerWidget::class.java)
                                .setAction(ACTION)
                                .putExtra("control", control),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        ),
                    )
                }
            manager.updateAppWidget(ids, views)
        }
    }
}
