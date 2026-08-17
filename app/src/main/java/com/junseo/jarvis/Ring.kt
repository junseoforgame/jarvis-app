package com.junseo.jarvis

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

/** 시간이 되면 시스템이 이 클래스를 깨운다. 앱이 꺼져 있어도 동작한다. */
class Ring : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Timer"
        val runId = intent.getIntExtra("runId", -1)

        val nm = context.getSystemService(NotificationManager::class.java)

        // 진행 중 알림을 걷어낸다
        try { if (runId > 0) nm?.cancel(runId) } catch (e: Throwable) {}

        try {
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(context, MainActivity.CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setColor(MainActivity.TINT)
                .setColorized(true)
                .setContentTitle("J.A.R.V.I.S.")
                .setContentText("$title complete.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            nm?.notify((System.currentTimeMillis() % 100000).toInt(), n)
        } catch (e: Throwable) {}

        try {
            var speaker: TextToSpeech? = null
            speaker = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val p = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                    speaker?.language = Locale.UK
                    speaker?.setSpeechRate(p.getFloat("speed", 1.04f))
                    speaker?.setPitch(p.getFloat("pitch", 0.88f))
                    val saved = p.getString("voice", "").orEmpty()
                    if (saved.isNotBlank()) {
                        speaker?.voices?.firstOrNull { it.name == saved }?.let { speaker?.voice = it }
                    }
                    speaker?.speak("$title complete.", TextToSpeech.QUEUE_FLUSH, null, "ring")
                }
            }
        } catch (e: Throwable) {}
    }
}
