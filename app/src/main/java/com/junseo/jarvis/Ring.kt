package com.junseo.jarvis

import android.app.NotificationManager
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

        try {
            val n = NotificationCompat.Builder(context, MainActivity.CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("J.A.R.V.I.S.")
                .setContentText("$title complete.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java)
                ?.notify((System.currentTimeMillis() % 100000).toInt(), n)
        } catch (e: Throwable) {}

        try {
            var speaker: TextToSpeech? = null
            speaker = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    speaker?.language = Locale.UK
                    speaker?.setSpeechRate(0.90f)
                    speaker?.setPitch(0.74f)
                    speaker?.speak("$title complete.", TextToSpeech.QUEUE_FLUSH, null, "ring")
                }
            }
        } catch (e: Throwable) {}
    }
}
