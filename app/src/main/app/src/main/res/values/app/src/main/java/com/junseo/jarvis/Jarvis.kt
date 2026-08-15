package com.junseo.jarvis

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.CallLog
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject



/**
 * í™”ë©´ì€ assets/index.html ì´ ê·¸ë¦¬ê³ ,
 * í° ê¸°ëŠ¥ì€ Bridge ë¥¼ í†µí•´ ê·¸ í™”ë©´ì—ì„œ ì§ì ‘ í˜¸ì¶œí•œë‹¤.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        /** í™”ë©´ì€ ì—¬ê¸°ì„œ ë¶ˆëŸ¬ì˜¨ë‹¤. ë””ìžì¸ì„ ê³ ì¹˜ë©´ ì´ ì£¼ì†Œë§Œ ê°±ì‹ í•˜ë©´ ëœë‹¤. */
        const val SCREEN_URL = "https://junseoforgame.github.io/Jarvis/"
    }


    private lateinit var web: WebView
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null

    private val perms = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ê³„ê¸°íŒì€ êº¼ì§€ë©´ ì•ˆ ëœë‹¤
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideBars()

        web = WebView(this)
        setContentView(web)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            databaseEnabled = true
        }
        web.setBackgroundColor(0xFF04030A.toInt())
        web.webViewClient = WebViewClient()
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { request.grant(request.resources) }
            }
        }

        web.addJavascriptInterface(Bridge(this, web), "Phone")
        web.loadUrl(SCREEN_URL)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.UK          // ì˜í™”ì— ê°€ê¹Œìš´ ì˜êµ­ì‹
                tts?.setSpeechRate(0.90f)
                tts?.setPitch(0.74f)
            }
        }

        askPermissions()
        BriefWorker.channel(this)
        scheduleBriefs()
    }

    /** ìžë¹„ìŠ¤ê°€ ìŠ¤ìŠ¤ë¡œ ìƒí™©ì„ ì‚´í”¼ëŠ” ì£¼ê¸°. ë°°í„°ë¦¬ë¥¼ ì•„ë¼ë ¤ í•œ ì‹œê°„ì— í•œ ë²ˆ. */
    private fun scheduleBriefs() {
        val work = PeriodicWorkRequestBuilder<BriefWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "jarvis-brief", ExistingPeriodicWorkPolicy.KEEP, work
        )
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
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        val all = if (Build.VERSION.SDK_INT >= 33)
            missing + Manifest.permission.POST_NOTIFICATIONS else missing
        if (all.isNotEmpty()) ActivityCompat.requestPermissions(this, all.toTypedArray(), 100)
    }

    /* â”€â”€ ìŒì„± ì¶œë ¥ â”€â”€ */
    fun speak(text: String) {
        tts?.stop()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    fun stopSpeaking() { tts?.stop() }

    /* â”€â”€ ìŒì„± ìž…ë ¥: ê²°ê³¼ë¥¼ í™”ë©´ìœ¼ë¡œ ë˜ëŒë ¤ ì¤€ë‹¤ â”€â”€ */
    fun listen() {
        runOnUiThread {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(p0: Bundle?) { toJs("onListenStart") }
                    override fun onRmsChanged(rms: Float) {
                        val level = ((rms + 2f) / 12f).coerceIn(0f, 1f)
                        toJs("onLevel", level.toString())
                    }
                    override fun onResults(results: Bundle?) {
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                        if (text.isBlank()) toJs("onListenEnd")
                        else toJs("onHeard", quote(text))
                    }
                    override fun onError(code: Int) { toJs("onListenEnd") }
                    override fun onBeginningOfSpeech() {}
                    override fun onBufferReceived(p0: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(p0: Bundle?) {}
                    override fun onEvent(p0: Int, p1: Bundle?) {}
                })
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")   // í•œêµ­ì–´ë¡œ ë“£ëŠ”ë‹¤
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer?.startListening(intent)
        }
    }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    fun toJs(fn: String, arg: String = "") {
        runOnUiThread {
            web.evaluateJavascript("window.$fn && window.$fn($arg)", null)
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }
}




/**
 * í™”ë©´(JS)ì—ì„œ window.Phone.xxx() ë¡œ í˜¸ì¶œí•œë‹¤.
 * ì—¬ê¸° ì—†ëŠ” ê¸°ëŠ¥ì€ ìžë¹„ìŠ¤ê°€ ì‹¤í–‰í•  ìˆ˜ ì—†ë‹¤.
 */
class Bridge(private val act: MainActivity, private val web: WebView) {

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(act, p) == PackageManager.PERMISSION_GRANTED

    /* â•â•â•â•â•â• ìŒì„± â•â•â•â•â•â• */

    @JavascriptInterface
    fun speak(text: String) = act.speak(text)

    @JavascriptInterface
    fun stopSpeaking() = act.stopSpeaking()

    @JavascriptInterface
    fun listen() = act.listen()

    /* â•â•â•â•â•â• ì—°ë½ â•â•â•â•â•â• */

    /** í†µí™” ê¸°ë¡ì„ ì„¸ì–´ ìžì£¼ ì—°ë½í•˜ëŠ” ì‚¬ëžŒ ìˆœìœ¼ë¡œ ëŒë ¤ì¤€ë‹¤ */
    @JavascriptInterface
    fun frequentContacts(limit: Int): String {
        if (!granted(Manifest.permission.READ_CALL_LOG)) return "[]"
        val tally = HashMap<String, Pair<String, Int>>()   // number -> (name, count)
        var lastSeen: Long = 0
        act.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE),
            null, null, CallLog.Calls.DATE + " DESC LIMIT 400"
        )?.use { c ->
            while (c.moveToNext()) {
                val num = c.getString(0) ?: continue
                val name = c.getString(1) ?: num
                val date = c.getLong(2)
                if (lastSeen == 0L) lastSeen = date
                val prev = tally[num]
                tally[num] = Pair(name, (prev?.second ?: 0) + 1)
            }
        }
        val arr = JSONArray()
        tally.entries.sortedByDescending { it.value.second }.take(limit).forEach {
            arr.put(JSONObject().apply {
                put("name", it.value.first)
                put("number", it.key)
                put("count", it.value.second)
            })
        }
        return arr.toString()
    }

    /** ì´ë¦„ìœ¼ë¡œ ì—°ë½ì²˜ë¥¼ ì°¾ëŠ”ë‹¤ */
    @JavascriptInterface
    fun findContact(name: String): String {
        if (!granted(Manifest.permission.READ_CONTACTS)) return "{}"
        val uri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(name)
        )
        act.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                return JSONObject().apply {
                    put("name", c.getString(0))
                    put("number", c.getString(1))
                }.toString()
            }
        }
        return "{}"
    }

    @JavascriptInterface
    fun call(number: String): String {
        if (!granted(Manifest.permission.CALL_PHONE)) return "call permission denied"
        val i = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        act.startActivity(i)
        return "dialling $number"
    }

    @SuppressLint("MissingPermission")
    @JavascriptInterface
    fun sms(number: String, body: String): String {
        if (!granted(Manifest.permission.SEND_SMS)) return "sms permission denied"
        return try {
            val sm = act.getSystemService(SmsManager::class.java)
            sm.sendTextMessage(number, null, body, null, null)
            "message sent"
        } catch (e: Exception) {
            "send failed: ${e.message}"
        }
    }

    /* â•â•â•â•â•â• ì‹œê°„ â•â•â•â•â•â• */

    @JavascriptInterface
    fun alarm(hour: Int, minute: Int, label: String): String {
        val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        act.startActivity(i)
        return "alarm set for %02d:%02d".format(hour, minute)
    }

    @JavascriptInterface
    fun timer(seconds: Int, label: String): String {
        val i = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        act.startActivity(i)
        return "timer running for $seconds seconds"
    }

    /* â•â•â•â•â•â• ì•± â•â•â•â•â•â• */

    @JavascriptInterface
    fun launch(query: String): String {
        val pm = act.packageManager
        val target = pm.getInstalledApplications(0).firstOrNull {
            val label = pm.getApplicationLabel(it).toString()
            label.equals(query, true) || label.contains(query, true) ||
                it.packageName.contains(query, true)
        } ?: return "no app matching \"$query\""
        val i = pm.getLaunchIntentForPackage(target.packageName)
            ?: return "cannot launch ${target.packageName}"
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        act.startActivity(i)
        return "opened ${pm.getApplicationLabel(target)}"
    }

    @JavascriptInterface
    fun openUrl(url: String): String {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        act.startActivity(i)
        return "opened"
    }

    /* â•â•â•â•â•â• ìƒíƒœ â•â•â•â•â•â• */

    @JavascriptInterface
    fun status(): String {
        val bm = act.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        return JSONObject().apply {
            put("battery", level)
            put("charging", charging)
        }.toString()
    }

    @SuppressLint("MissingPermission")
    @JavascriptInterface
    fun location(): String {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) return "{}"
        val lm = act.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: return "{}"
        return JSONObject().apply {
            put("lat", loc.latitude)
            put("lon", loc.longitude)
        }.toString()
    }

    /* â•â•â•â•â•â• PC ê¹¨ìš°ê¸° â•â•â•â•â•â• */

    @JavascriptInterface
    fun wakePc(mac: String, broadcast: String): String {
        return try {
            val bytes = mac.split(":", "-").map { it.toInt(16).toByte() }
            val packet = ByteArray(6) { 0xFF.toByte() } +
                ByteArray(16 * 6) { bytes[it % 6] }
            val addr = InetAddress.getByName(if (broadcast.isBlank()) "255.255.255.255" else broadcast)
            DatagramSocket().use { s ->
                s.broadcast = true
                s.send(DatagramPacket(packet, packet.size, addr, 9))
            }
            "magic packet sent"
        } catch (e: Exception) {
            "wake failed: ${e.message}"
        }
    }

    /* â•â•â•â•â•â• ì•Œë¦¼ Â· ì˜ˆì‚° â•â•â•â•â•â• */

    /** ìžë¹„ìŠ¤ê°€ ì§ì ‘ ì•Œë¦¼ì„ ë„ìš´ë‹¤ */
    @JavascriptInterface
    fun notify(text: String): String {
        BriefWorker.channel(act)
        val open = android.app.PendingIntent.getActivity(
            act, 0, Intent(act, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val n = androidx.core.app.NotificationCompat.Builder(act, BriefWorker.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("J.A.R.V.I.S.")
            .setContentText(text)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return try {
            androidx.core.app.NotificationManagerCompat.from(act)
                .notify((System.currentTimeMillis() % 100000).toInt(), n)
            "notified"
        } catch (e: SecurityException) { "notification permission denied" }
    }

    /** í™”ë©´ì—ì„œ ì„¤ì •í•œ ê°’ì„ ë°°ê²½ ìž‘ì—…ë„ ì“¸ ìˆ˜ ìžˆê²Œ ì €ìž¥í•œë‹¤ */
    @JavascriptInterface
    fun savePref(k: String, v: String) {
        act.getSharedPreferences(BriefWorker.PREFS, Context.MODE_PRIVATE)
            .edit().putString(k, v).apply()
    }

    @JavascriptInterface
    fun saveHome(lat: Double, lon: Double) {
        act.getSharedPreferences(BriefWorker.PREFS, Context.MODE_PRIVATE)
            .edit().putFloat("homeLat", lat.toFloat())
            .putFloat("homeLon", lon.toFloat()).apply()
    }

    /** ì˜¤ëŠ˜ ì“´ í† í°ì„ ëˆ„ì í•˜ê³  ë‚¨ì€ ì˜ˆì‚° ìƒíƒœë¥¼ ëŒë ¤ì¤€ë‹¤ */
    @JavascriptInterface
    fun spend(tokens: Int): String {
        val p = act.getSharedPreferences(BriefWorker.PREFS, Context.MODE_PRIVATE)
        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        if (p.getInt("day", -1) != today) p.edit().putInt("day", today).putInt("spent", 0).apply()
        val spent = p.getInt("spent", 0) + tokens
        p.edit().putInt("spent", spent).apply()
        val cap = p.getInt("cap", 60000)
        return JSONObject().apply {
            put("spent", spent); put("cap", cap)
            put("pct", (spent * 100 / cap).coerceAtMost(999))
        }.toString()
    }

    @JavascriptInterface
    fun setCap(cap: Int) {
        act.getSharedPreferences(BriefWorker.PREFS, Context.MODE_PRIVATE)
            .edit().putInt("cap", cap).apply()
    }
}




/**
 * ì£¼ê¸°ì ìœ¼ë¡œ ìƒí™©ì„ ì‚´íŽ´ë³´ê³ , ë§í•  ê°€ì¹˜ê°€ ìžˆì„ ë•Œë§Œ ì•Œë¦¼ì„ ë„ìš´ë‹¤.
 *
 * ë¹„ìš© ì›ì¹™
 *  - í•˜ë£¨ ìƒí•œì„ ë„˜ê¸°ë©´ ì•„ì˜ˆ í˜¸ì¶œí•˜ì§€ ì•ŠëŠ”ë‹¤.
 *  - ê°€ìž¥ ì €ë ´í•œ ëª¨ë¸ë§Œ ì“´ë‹¤.
 *  - í•  ë§ì´ ì—†ìœ¼ë©´ ëª¨ë¸ì´ SKIP ì„ ë°˜í™˜í•˜ê³ , ì•Œë¦¼ì€ ëœ¨ì§€ ì•ŠëŠ”ë‹¤.
 */
class BriefWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val CHANNEL = "jarvis"
        const val PREFS = "jarvis"

        fun channel(ctx: Context) {
            val ch = NotificationChannel(
                CHANNEL, "JARVIS",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Unprompted remarks from JARVIS" }
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private val prefs by lazy { applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override suspend fun doWork(): Result {
        val key = prefs.getString("key", "").orEmpty()
        if (key.isBlank()) return Result.success()

        // ì‚¬ìš©ìžê°€ ë„ë©´ ì•„ë¬´ê²ƒë„ í•˜ì§€ ì•ŠëŠ”ë‹¤
        val policy = prefs.getString("unprompted", "important")
        if (policy == "off") return Result.success()

        // ì¡°ìš©í•œ ì‹œê°„ì—ëŠ” ë§ì„ ê±¸ì§€ ì•ŠëŠ”ë‹¤
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 8 || hour >= 22) return Result.success()

        // â”€â”€ ë¨¼ì € ë¡œì»¬ì—ì„œ ì‚¬ê±´ì´ ìžˆì—ˆëŠ”ì§€ ë³¸ë‹¤. ì—†ìœ¼ë©´ ëª¨ë¸ì„ ë¶€ë¥´ì§€ ì•ŠëŠ”ë‹¤ â”€â”€
        val event = detectEvent(policy) ?: return Result.success()

        // â”€â”€ í•˜ë£¨ ì˜ˆì‚° í™•ì¸ â”€â”€
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (prefs.getInt("day", -1) != today) {
            prefs.edit().putInt("day", today).putInt("spent", 0).apply()
        }
        val cap = prefs.getInt("cap", 60000)
        if (prefs.getInt("spent", 0) >= cap) return Result.success()

        val reply = askHaiku(key, event) ?: return Result.success()
        if (reply.isBlank() || reply.trim().startsWith("SKIP")) return Result.success()

        notify(reply.trim())
        return Result.success()
    }

    /**
     * ë§ì„ ê±¸ ì´ìœ ê°€ ì‹¤ì œë¡œ ìƒê²¼ì„ ë•Œë§Œ ë¬¸ìžì—´ì„ ëŒë ¤ì¤€ë‹¤.
     * ì‹¬ì‹¬í•´ì„œ ê±°ëŠ” ì¼ì€ ì—†ë‹¤.
     */
    @SuppressLint("MissingPermission")
    private fun detectEvent(policy: String): String? {
        val now = System.currentTimeMillis()
        val ed = prefs.edit()

        // 1. ì§‘ì— ë§‰ ë„ì°©í–ˆë‹¤
        if (has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            val lat = prefs.getFloat("homeLat", 0f)
            val lon = prefs.getFloat("homeLon", 0f)
            if (lat != 0f) {
                val lm = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (loc != null) {
                    val home = Location("home").apply {
                        latitude = lat.toDouble(); longitude = lon.toDouble()
                    }
                    val near = loc.distanceTo(home) < 150f
                    val wasNear = prefs.getBoolean("wasHome", false)
                    ed.putBoolean("wasHome", near).apply()
                    if (near && !wasNear) return "The user has just arrived home."
                }
            }
        }

        // 2. ë°–ì¸ë° ë°°í„°ë¦¬ê°€ ìœ„í—˜í•˜ë‹¤ (í•˜ë£¨ í•œ ë²ˆ)
        val bm = applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level in 1..15 && !bm.isCharging && !prefs.getBoolean("wasHome", true)) {
            val last = prefs.getLong("lastBatt", 0)
            if (now - last > 20 * 3600_000L) {
                ed.putLong("lastBatt", now).apply()
                return "Phone battery is at $level percent and the user is away from home."
            }
        }

        // 3. ì˜¤ëž˜ ì—°ë½ì´ ëŠê¸´ ì‚¬ëžŒ (ì‚¬í˜ì— í•œ ë²ˆê¹Œì§€ë§Œ, important ì´ìƒ)
        if (policy == "on" || policy == "important") {
            if (has(Manifest.permission.READ_CALL_LOG)) {
                val last = prefs.getLong("lastGap", 0)
                if (now - last > 3 * 86_400_000L) {
                    gapContact()?.let {
                        ed.putLong("lastGap", now).apply()
                        return it
                    }
                }
            }
        }

        return null
    }

    private fun has(p: String) =
        ContextCompat.checkSelfPermission(applicationContext, p) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun gapContact(): String? {
        val tally = HashMap<String, Triple<String, Int, Long>>()
        applicationContext.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE),
            null, null, CallLog.Calls.DATE + " DESC LIMIT 300"
        )?.use { cur ->
            while (cur.moveToNext()) {
                val num = cur.getString(0) ?: continue
                val name = cur.getString(1) ?: continue
                val date = cur.getLong(2)
                val prev = tally[num]
                tally[num] = Triple(name, (prev?.second ?: 0) + 1, prev?.third ?: date)
            }
        }
        val top = tally.values.filter { it.second >= 3 }.maxByOrNull { it.second } ?: return null
        val days = ((System.currentTimeMillis() - top.third) / 86_400_000L).toInt()
        return if (days >= 14) "The user has not spoken to ${top.first} in $days days." else null
    }

    /* â”€â”€ ê°€ìž¥ ì €ë ´í•œ ëª¨ë¸ì—ê²Œ í•œ ì¤„ë§Œ ë¬»ëŠ”ë‹¤ â”€â”€ */
    private fun askHaiku(key: String, context: String): String? {
        val name = prefs.getString("name", "sir")
        val sys = """
            You are J.A.R.V.I.S., a butler AI. You may address the user unprompted.
            You are given ONE event that has just occurred. React to it in ONE short
            English sentence, at most fifteen words, formal and dry. Offer a useful
            next step if there is an obvious one. Never chatter, never make small talk.
            If the event does not warrant interrupting the user, reply exactly: SKIP
            Address the user as "$name". No emoji, no markdown.
        """.trimIndent()

        val body = JSONObject().apply {
            put("model", "claude-haiku-4-5-20251001")
            put("max_tokens", 60)
            put("system", sys)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", "Event: $context")
            }))
        }.toString()

        return try {
            val conn = (URL("https://api.anthropic.com/v1/messages").openConnection()
                as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("content-type", "application/json")
                setRequestProperty("x-api-key", key)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())

            json.optJSONObject("usage")?.let { u ->
                val used = u.optInt("input_tokens") + u.optInt("output_tokens")
                prefs.edit().putInt("spent", prefs.getInt("spent", 0) + used).apply()
            }

            val arr = json.optJSONArray("content") ?: return null
            (0 until arr.length())
                .map { arr.getJSONObject(it) }
                .firstOrNull { it.optString("type") == "text" }
                ?.optString("text")
        } catch (e: Exception) { null }
    }

    private fun notify(text: String) {
        if (!has(Manifest.permission.POST_NOTIFICATIONS)) return
        val open = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("J.A.R.V.I.S.")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(
            (System.currentTimeMillis() % 100000).toInt(), n
        )
    }
}

