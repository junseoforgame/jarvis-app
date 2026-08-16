package com.junseo.jarvis

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.Locale

/**
 * 화면은 웹에서 불러오고, 폰 기능만 여기서 연결한다.
 * 어느 한 기능이 실패해도 앱이 죽지 않도록 전부 감싼다.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val SCREEN_URL = "https://junseoforgame.github.io/Jarvis/"
        const val CHANNEL = "jarvis"
        const val PREFS = "jarvis"
    }

    private var web: WebView? = null
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try { window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } catch (e: Throwable) {}
        try { hideBars() } catch (e: Throwable) {}

        val w = WebView(this)
        web = w
        setContentView(w)

        try {
            w.settings.javaScriptEnabled = true
            w.settings.domStorageEnabled = true
            w.settings.mediaPlaybackRequiresUserGesture = false
        } catch (e: Throwable) {}

        w.setBackgroundColor(0xFF0A0908.toInt())
        w.webViewClient = WebViewClient()
        w.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { try { request.grant(request.resources) } catch (e: Throwable) {} }
            }
        }

        try { w.addJavascriptInterface(Bridge(), "Phone") } catch (e: Throwable) {}
        try { w.loadUrl(SCREEN_URL) } catch (e: Throwable) {}

        try {
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        tts?.language = Locale.UK
                        tts?.setSpeechRate(0.90f)
                        tts?.setPitch(0.74f)
                    } catch (e: Throwable) {}
                }
            }
        } catch (e: Throwable) {}

        try { makeChannel() } catch (e: Throwable) {}
        try { askPermissions() } catch (e: Throwable) {}
    }

    private fun makeChannel() {
        val ch = NotificationChannel(CHANNEL, "JARVIS", NotificationManager.IMPORTANCE_HIGH)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }

    private fun hideBars() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    private fun askPermissions() {
        val wanted = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS

        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun toJs(fn: String, arg: String = "") {
        runOnUiThread {
            try { web?.evaluateJavascript("window.$fn && window.$fn($arg)", null) } catch (e: Throwable) {}
        }
    }

    private fun quote(s: String) =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    override fun onDestroy() {
        try { recognizer?.destroy() } catch (e: Throwable) {}
        try { tts?.shutdown() } catch (e: Throwable) {}
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) try { hideBars() } catch (e: Throwable) {}
    }

    /* ══════════════════════════════════════════
       화면에서 부르는 다리
       ══════════════════════════════════════════ */
    inner class Bridge {

        @JavascriptInterface
        fun speak(text: String) {
            try {
                tts?.stop()
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
            } catch (e: Throwable) {}
        }

        @JavascriptInterface
        fun stopSpeaking() { try { tts?.stop() } catch (e: Throwable) {} }

        @JavascriptInterface
        fun listen() {
            runOnUiThread {
                try {
                    recognizer?.destroy()
                    val r = SpeechRecognizer.createSpeechRecognizer(this@MainActivity)
                    recognizer = r
                    r.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(p0: Bundle?) { toJs("onListenStart") }
                        override fun onRmsChanged(rms: Float) {
                            val lv = ((rms + 2f) / 12f).coerceIn(0f, 1f)
                            toJs("onLevel", lv.toString())
                        }
                        override fun onResults(results: Bundle?) {
                            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull().orEmpty()
                            if (t.isBlank()) toJs("onListenEnd") else toJs("onHeard", quote(t))
                        }
                        override fun onError(code: Int) { toJs("onListenEnd") }
                        override fun onBeginningOfSpeech() {}
                        override fun onBufferReceived(p0: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onPartialResults(p0: Bundle?) {}
                        override fun onEvent(p0: Int, p1: Bundle?) {}
                    })
                    val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    r.startListening(i)
                } catch (e: Throwable) { toJs("onListenEnd") }
            }
        }

        @JavascriptInterface
        fun findContact(name: String): String {
            return try {
                if (!granted(Manifest.permission.READ_CONTACTS)) return "{}"
                val uri = Uri.withAppendedPath(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(name))
                contentResolver.query(uri, arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        return JSONObject().apply {
                            put("name", c.getString(0)); put("number", c.getString(1))
                        }.toString()
                    }
                }
                "{}"
            } catch (e: Throwable) { "{}" }
        }

        @JavascriptInterface
        fun frequentContacts(limit: Int): String = "[]"   // 통화기록 권한 없이는 비활성

        @JavascriptInterface
        fun call(number: String): String {
            return try {
                if (!granted(Manifest.permission.CALL_PHONE)) return "call permission denied"
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "dialling"
            } catch (e: Throwable) { "call failed" }
        }

        @JavascriptInterface
        fun sms(number: String, body: String): String = "messaging is disabled in this build"

        @JavascriptInterface
        fun alarm(hour: Int, minute: Int, label: String): String {
            return try {
                val now = java.util.Calendar.getInstance()
                val at = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                    set(java.util.Calendar.MINUTE, minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                    if (timeInMillis <= now.timeInMillis) add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                countdown(at.timeInMillis - now.timeInMillis, "Alarm")
                "alarm set for %02d:%02d".format(hour, minute)
            } catch (e: Throwable) { "alarm failed" }
        }

        @JavascriptInterface
        fun timer(seconds: Int, label: String): String {
            return try {
                countdown(seconds * 1000L, "Timer")
                "timer running for $seconds seconds"
            } catch (e: Throwable) { "timer failed" }
        }

        /** 시스템에 시간을 맡긴다. 앱이 꺼져도, 폰을 껐다 켜도 제 시간에 울린다. */
        private fun countdown(ms: Long, title: String) {
            val at = System.currentTimeMillis() + ms.coerceAtLeast(1000L)
            val intent = Intent(this@MainActivity, Ring::class.java).putExtra("title", title)
            val pi = PendingIntent.getBroadcast(
                this@MainActivity,
                (at % 100000).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            try {
                val exact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (e: Throwable) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }

        @JavascriptInterface
        fun launch(query: String): String {
            return try {
                val pm = packageManager
                val app = pm.getInstalledApplications(0).firstOrNull {
                    val label = pm.getApplicationLabel(it).toString()
                    label.equals(query, true) || label.contains(query, true)
                } ?: return "no app matching $query"
                val i = pm.getLaunchIntentForPackage(app.packageName) ?: return "cannot launch"
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "opened " + pm.getApplicationLabel(app)
            } catch (e: Throwable) { "launch failed" }
        }

        @JavascriptInterface
        fun openUrl(url: String): String {
            return try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "opened"
            } catch (e: Throwable) { "open failed" }
        }

        @JavascriptInterface
        fun status(): String {
            return try {
                val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                JSONObject().apply {
                    put("battery", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
                    put("charging", bm.isCharging)
                }.toString()
            } catch (e: Throwable) { "{}" }
        }

        @JavascriptInterface
        fun location(): String = "{}"

        @JavascriptInterface
        fun wakePc(mac: String, broadcast: String): String = "wake-on-lan is disabled in this build"

        @JavascriptInterface
        fun notify(text: String): String {
            return try {
                val n = NotificationCompat.Builder(this@MainActivity, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("J.A.R.V.I.S.")
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()
                NotificationManagerCompat.from(this@MainActivity)
                    .notify((System.currentTimeMillis() % 100000).toInt(), n)
                "notified"
            } catch (e: Throwable) { "notify failed" }
        }

        @JavascriptInterface
        fun savePref(k: String, v: String) {
            try {
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(k, v).apply()
            } catch (e: Throwable) {}
        }

        @JavascriptInterface
        fun saveHome(lat: Double, lon: Double) {}

        @JavascriptInterface
        fun setCap(cap: Int) {
            try {
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("cap", cap).apply()
            } catch (e: Throwable) {}
        }

        @JavascriptInterface
        fun spend(tokens: Int): String {
            return try {
                val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
                if (p.getInt("day", -1) != today)
                    p.edit().putInt("day", today).putInt("spent", 0).apply()
                val spent = p.getInt("spent", 0) + tokens
                p.edit().putInt("spent", spent).apply()
                val cap = p.getInt("cap", 60000)
                JSONObject().apply {
                    put("spent", spent); put("cap", cap)
                    put("pct", (spent * 100 / cap).coerceAtMost(999))
                }.toString()
            } catch (e: Throwable) { """{"spent":0,"cap":60000,"pct":0}""" }
        }
    }
}
