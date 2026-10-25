package app.flowplay

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// ═══════════════════════════ الموديلات ═══════════════════════════

class Vid(
    val id: Long, val uri: String, val title: String, val dur: Long, val size: Long,
    val w: Int, val h: Int, val added: Long, val folder: String
)

class Song(
    val id: Long, val uri: String, val title: String, val artist: String, val album: String,
    val albumId: Long, val dur: Long, val added: Long, val folder: String
)

class Playlist(val id: String, val name: String, val ids: List<Long>)

// ═══════════════════════════ الإعدادات ═══════════════════════════

object Prefs {
    private lateinit var sp: SharedPreferences

    // المظهر
    var theme by mutableIntStateOf(0)            // 0 النظام، 1 فاتح، 2 داكن
    var accent by mutableIntStateOf(0)           // 0 لون الخلفية، 1..5 ألوان ثابتة

    // الفيديو
    var resume by mutableStateOf(true)           // كمّل من مكان التوقف
    var autoNext by mutableStateOf(true)         // شغّل الفيديو اللي بعده تلقائي
    var gestures by mutableStateOf(true)         // لمسات السطوع والصوت والتقديم
    var seekSec by mutableIntStateOf(10)         // ثواني الضغط المزدوج
    var autoRotate by mutableStateOf(true)       // لفّ الشاشة حسب شكل الفيديو
    var openOrient by mutableIntStateOf(1)       // اتجاه الشاشة عند فتح الفيديو: 0 حسب الفيديو، 1 طولي، 2 عرضي
    var keepOn by mutableStateOf(true)           // خلّي الشاشة منوّرة
    var pipAuto by mutableStateOf(true)          // نافذة عايمة لما تخرج
    var aspect by mutableIntStateOf(0)           // 0 مناسب، 1 ملء، 2 قص
    var subSize by mutableIntStateOf(1)          // 0 صغير، 1 وسط، 2 كبير
    var longSpeed by mutableStateOf(true)        // ضغطة مطوّلة = سرعة 2x
    var defaultSpeed by mutableFloatStateOf(1f)
    var dtCenter by mutableStateOf(true)         // ضغطتين في النص = تشغيل/إيقاف
    var subColor by mutableIntStateOf(0)         // 0 أبيض، 1 أصفر، 2 سماوي
    var subBg by mutableStateOf(false)           // خلفية سودا للترجمة
    var amoled by mutableStateOf(false)          // وضع AMOLED أسود تمامًا
    var skipSilence by mutableStateOf(false)     // تخطي الصمت في الموسيقى
    var skipIntro by mutableIntStateOf(85)       // ثواني زر التخطي في الفيديو
    var videoListStyle by mutableIntStateOf(0)   // 0 شبكة، 1 قائمة يوتيوب

    // الموسيقى
    var minAudioSec by mutableIntStateOf(0)     // تجاهل الملفات الأقصر من كده
    var restoreQueue by mutableStateOf(true)     // رجّع آخر قائمة تشغيل
    var songSort by mutableIntStateOf(0)         // 0 الاسم، 1 الفنان، 2 الأحدث، 3 المدة
    var vidSort by mutableIntStateOf(0)          // 0 الأحدث، 1 الاسم، 2 الأطول، 3 الأكبر

    // المعادل الصوتي
    var eqOn by mutableStateOf(false)
    var eqLevels by mutableStateOf("")
    var bassStr by mutableIntStateOf(0)
    var virtStr by mutableIntStateOf(0)

    // آخر جلسة موسيقى
    var lastQueue by mutableStateOf("")
    var lastIndex by mutableIntStateOf(0)
    var lastPos by mutableLongStateOf(0L)

    fun load(c: Context) {
        sp = c.getSharedPreferences("flow", 0)
        theme = sp.getInt("tm", 0); accent = sp.getInt("ac", 0)
        resume = sp.getBoolean("rs", true); autoNext = sp.getBoolean("an", true); gestures = sp.getBoolean("gs", true)
        seekSec = sp.getInt("sk", 10); autoRotate = sp.getBoolean("ar", true); keepOn = sp.getBoolean("ko", true)
        pipAuto = sp.getBoolean("pp", true); aspect = sp.getInt("as", 0); subSize = sp.getInt("ss", 1)
        longSpeed = sp.getBoolean("ls", true); defaultSpeed = sp.getFloat("ds", 1f)
        dtCenter = sp.getBoolean("dc", true); subColor = sp.getInt("sc", 0); subBg = sp.getBoolean("sb", false)
        amoled = sp.getBoolean("am", false); skipSilence = sp.getBoolean("sl", false); skipIntro = sp.getInt("si", 85)
        videoListStyle = sp.getInt("vls", 0); openOrient = sp.getInt("oo", 1)
        minAudioSec = sp.getInt("ma", 0); restoreQueue = sp.getBoolean("rq", true)
        songSort = sp.getInt("so", 0); vidSort = sp.getInt("vo", 0)
        eqOn = sp.getBoolean("eo", false); eqLevels = sp.getString("el", "") ?: ""
        bassStr = sp.getInt("bs", 0); virtStr = sp.getInt("vs", 0)
        lastQueue = sp.getString("lq", "") ?: ""; lastIndex = sp.getInt("li", 0); lastPos = sp.getLong("lp", 0L)
    }

    fun save() {
        if (!::sp.isInitialized) return
        sp.edit().putInt("tm", theme).putInt("ac", accent)
            .putBoolean("rs", resume).putBoolean("an", autoNext).putBoolean("gs", gestures)
            .putInt("sk", seekSec).putBoolean("ar", autoRotate).putBoolean("ko", keepOn)
            .putBoolean("pp", pipAuto).putInt("as", aspect).putInt("ss", subSize)
            .putBoolean("ls", longSpeed).putFloat("ds", defaultSpeed)
            .putBoolean("dc", dtCenter).putInt("sc", subColor).putBoolean("sb", subBg)
            .putBoolean("am", amoled).putBoolean("sl", skipSilence).putInt("si", skipIntro)
            .putInt("vls", videoListStyle).putInt("oo", openOrient)
            .putInt("ma", minAudioSec).putBoolean("rq", restoreQueue)
            .putInt("so", songSort).putInt("vo", vidSort)
            .putBoolean("eo", eqOn).putString("el", eqLevels).putInt("bs", bassStr).putInt("vs", virtStr)
            .putString("lq", lastQueue).putInt("li", lastIndex).putLong("lp", lastPos)
            .apply()
    }
}

// ═══════════════════════════ فحص الملفات من MediaStore ════════════════════════[...]

object Media {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    var videos by mutableStateOf<List<Vid>>(emptyList())
    var songs by mutableStateOf<List<Song>>(emptyList())
    var loaded by mutableStateOf(false)
    var byId: Map<Long, Song> = emptyMap()
    private var busy = false

    fun refresh(c: Context) {
        if (busy) return
        busy = true
        val app = c.applicationContext
        scope.launch {
            try { scan(app) } catch (_: Throwable) {} finally { busy = false }
        }
    }

    private suspend fun scan(c: Context) {
        val pair = withContext(Dispatchers.IO) { Pair(queryVideos(c), querySongs(c)) }
        byId = pair.second.associateBy { it.id }
        videos = pair.first
        songs = pair.second
        loaded = true
    }

    private fun queryVideos(c: Context): List<Vid> {
        val out = ArrayList<Vid>()
        val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE, MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )
        try {
            c.contentResolver.query(base, proj, null, null, MediaStore.Video.Media.DATE_ADDED + " DESC")?.use { q ->
                val iId = q.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val iName = q.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val iDur = q.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val iSize = q.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val iW = q.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val iH = q.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val iAdd = q.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val iBk = q.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                while (q.moveToNext()) {
                    val id = q.getLong(iId)
                    val name = q.getString(iName) ?: "video $id"
                    out.add(
                        Vid(
                            id, ContentUris.withAppendedId(base, id).toString(), name.substringBeforeLast('.'),
                            q.getLong(iDur), q.getLong(iSize), q.getInt(iW), q.getInt(iH), q.getLong(iAdd),
                            q.getString(iBk) ?: "غير مصنّف"
                        )
                    )
                }
            }
        } catch (_: Throwable) {}
        return out
    }

    private fun querySongs(c: Context): List<Song> {
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED, MediaStore.MediaColumns.RELATIVE_PATH
        )
        val minMs = Prefs.minAudioSec * 1000L
        // المحاولة الأولى: بفلتر بسيط من غير ترتيب SQL (الترتيب بـ COLLATE بيرمي خطأ على أندرويد الجديد)
        // والمحاولة التانية: من غير أي فلتر، والفلترة بتتم هنا في الكود
        val attempts = listOf<Pair<String?, Array<String>?>>(
            Pair("(" + MediaStore.Audio.Media.IS_RINGTONE + " IS NULL OR " + MediaStore.Audio.Media.IS_RINGTONE + " = 0) AND (" +
                MediaStore.Audio.Media.IS_NOTIFICATION + " IS NULL OR " + MediaStore.Audio.Media.IS_NOTIFICATION + " = 0) AND (" +
                MediaStore.Audio.Media.IS_ALARM + " IS NULL OR " + MediaStore.Audio.Media.IS_ALARM + " = 0)", null),
            Pair(null, null)
        )
        for ((sel, args) in attempts) {
            val out = ArrayList<Song>()
            try {
                c.contentResolver.query(base, proj, sel, args, null)?.use { q ->
                    val iId = q.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val iTi = q.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val iAr = q.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val iAl = q.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val iAi = q.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val iDu = q.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val iAd = q.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val iRp = q.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    while (q.moveToNext()) {
                        val dur = if (q.isNull(iDu)) 0L else q.getLong(iDu)
                        if (dur in 1 until minMs) continue
                        val id = q.getLong(iId)
                        val artist = q.getString(iAr)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "فنان غير معروف"
                        val album = q.getString(iAl)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "ألبوم غير معروف"
                        val rp = if (iRp >= 0) q.getString(iRp) else null
                        out.add(
                            Song(
                                id, ContentUris.withAppendedId(base, id).toString(),
                                q.getString(iTi)?.takeIf { it.isNotBlank() } ?: "بدون عنوان",
                                artist, album, q.getLong(iAi), dur, q.getLong(iAd),
                                rp?.trim('/')?.substringAfterLast('/') ?: ""
                            )
                        )
                    }
                }
            } catch (_: Throwable) {}
            if (out.isNotEmpty()) return out.sortedBy { it.title.lowercase() }
        }
        return emptyList()
    }
}

// حذف ملفات: أندرويد 11+ بيطلب موافقة المستخدم عن طريق نافذة النظام
fun deleteMedia(c: Context, uris: List<Uri>, launch: (IntentSender) -> Unit): Boolean = try {
    if (Build.VERSION.SDK_INT >= 30) {
        launch(MediaStore.createDeleteRequest(c.contentResolver, uris).intentSender)
    } else {
        uris.forEach { c.contentResolver.delete(it, null, null) }
        Media.refresh(c)
    }
    true
} catch (_: Throwable) { false }

// ═══════════════════════════ المفضلة + القوائم + السجل + أماكن التوقف ════════════════[...]

object Store {
    private lateinit var sp: SharedPreferences
    private lateinit var vpSp: SharedPreferences
    private lateinit var vtSp: SharedPreferences

    var favs by mutableStateOf<Set<Long>>(emptySet())
    var vfavs by mutableStateOf<Set<String>>(emptySet())   // مفضلة الفيديو (بالـ uri)
    val playlists = mutableStateListOf<Playlist>()
    var plays by mutableStateOf<Map<Long, Int>>(emptyMap())
    var recent by mutableStateOf<List<Long>>(emptyList())
    val vpos = mutableStateMapOf<String, Long>()     // مكان توقف كل فيديو
    val vlast = mutableStateMapOf<String, Long>()    // آخر وقت شاهدته

    fun init(c: Context) {
        sp = c.getSharedPreferences("store", 0)
        vpSp = c.getSharedPreferences("vpos", 0)
        vtSp = c.getSharedPreferences("vlast", 0)
        favs = sp.getString("favs", "")!!.split(',').mapNotNull { it.toLongOrNull() }.toSet()
        vfavs = try { JSONArray(sp.getString("vfavs", "[]")).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } } catch (_: Throwable) { emptySet() }
        recent = sp.getString("recent", "")!!.split(',').mapNotNull { it.toLongOrNull() }
        plays = sp.getString("plays", "")!!.split(',').mapNotNull {
            val p = it.split(':'); if (p.size == 2) p[0].toLongOrNull()?.let { k -> k to (p[1].toIntOrNull() ?: 0) } else null
        }.toMap()
        playlists.clear()
        try {
            val arr = JSONArray(sp.getString("pls", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ids = o.getJSONArray("ids")
                playlists.add(Playlist(o.getString("id"), o.getString("name"), (0 until ids.length()).map { ids.getLong(it) }))
            }
        } catch (_: Throwable) {}
        vpos.clear(); vlast.clear()
        for ((k, v) in vpSp.all) if (v is Long) vpos[k] = v
        for ((k, v) in vtSp.all) if (v is Long) vlast[k] = v
    }

    // ─── المفضلة ───
    fun isFav(id: Long) = id in favs
    fun toggleFav(id: Long) {
        favs = if (id in favs) favs - id else favs + id
        sp.edit().putString("favs", favs.joinToString(",")).apply()
    }

    fun isVFav(uri: String) = uri in vfavs
    fun toggleVFav(uri: String) {
        vfavs = if (uri in vfavs) vfavs - uri else vfavs + uri
        sp.edit().putString("vfavs", JSONArray(vfavs.toList()).toString()).apply()
    }

    // ─── نسخ احتياطي: المفضلة + القوائم + عدّاد التشغيل ───
    fun exportAll(): String {
        val o = JSONObject()
        o.put("app", "Next")
        o.put("favs", JSONArray(favs.toList()))
        o.put("vfavs", JSONArray(vfavs.toList()))
        o.put("plays", JSONObject().also { j -> plays.forEach { (k, v) -> j.put(k.toString(), v) } })
        val arr = JSONArray()
        playlists.forEach { p -> arr.put(JSONObject().put("id", p.id).put("name", p.name).put("ids", JSONArray(p.ids))) }
        o.put("pls", arr)
        return o.toString()
    }

    fun importAll(text: String): Boolean = try {
        val o = JSONObject(text)
        if (o.optString("app") != "Next") false else {
            val f = o.optJSONArray("favs"); val vf = o.optJSONArray("vfavs")
            if (f != null) favs = favs + (0 until f.length()).map { f.getLong(it) }
            if (vf != null) vfavs = vfavs + (0 until vf.length()).map { vf.getString(it) }
            val pl = o.optJSONArray("pls")
            if (pl != null) for (i in 0 until pl.length()) {
                val p = pl.getJSONObject(i)
                if (playlists.none { it.id == p.getString("id") }) {
                    val ids = p.getJSONArray("ids")
                    playlists.add(Playlist(p.getString("id"), p.getString("name"), (0 until ids.length()).map { ids.getLong(it) }))
                }
            }
            sp.edit().putString("favs", favs.joinToString(",")).putString("vfavs", JSONArray(vfavs.toList()).toString()).apply()
            savePlaylists()
            true
        }
    } catch (_: Throwable) { false }

    fun clearWatchHistory() {
        vpos.clear(); vlast.clear()
        vpSp.edit().clear().apply(); vtSp.edit().clear().apply()
    }

    fun clearPlayHistory() {
        plays = emptyMap(); recent = emptyList()
        sp.edit().remove("plays").remove("recent").apply()
    }

    // ─── القوائم ───
    private fun savePlaylists() {
        val arr = JSONArray()
        playlists.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("ids", JSONArray(p.ids)))
        }
        sp.edit().putString("pls", arr.toString()).apply()
    }

    fun createPlaylist(name: String, ids: List<Long> = emptyList()): String {
        val id = System.nanoTime().toString()
        playlists.add(Playlist(id, name.trim().ifEmpty { "قائمة جديدة" }, ids.distinct()))
        savePlaylists()
        return id
    }

    fun addToPlaylist(pid: String, ids: List<Long>) {
        val i = playlists.indexOfFirst { it.id == pid }
        if (i < 0) return
        val p = playlists[i]
        playlists[i] = Playlist(p.id, p.name, (p.ids + ids).distinct())
        savePlaylists()
    }

    fun removeFromPlaylist(pid: String, songId: Long) {
        val i = playlists.indexOfFirst { it.id == pid }
        if (i < 0) return
        val p = playlists[i]
        playlists[i] = Playlist(p.id, p.name, p.ids - songId)
        savePlaylists()
    }

    fun renamePlaylist(pid: String, name: String) {
        val i = playlists.indexOfFirst { it.id == pid }
        if (i < 0) return
        val p = playlists[i]
        playlists[i] = Playlist(p.id, name.trim().ifEmpty { p.name }, p.ids)
        savePlaylists()
    }

    fun deletePlaylist(pid: String) {
        playlists.removeAll { it.id == pid }
        savePlaylists()
    }

    // ─── عدّاد التشغيل والسجل ───
    fun countPlay(id: Long) {
        if (id <= 0) return
        plays = plays + (id to ((plays[id] ?: 0) + 1))
        recent = (listOf(id) + recent.filter { it != id }).take(60)
        sp.edit().putString("plays", plays.entries.joinToString(",") { "${it.key}:${it.value}" })
            .putString("recent", recent.joinToString(",")).apply()
    }

    // ─── أماكن توقف الفيديو ───
    fun videoPos(uri: String): Long = vpos[uri] ?: 0L

    fun setVideoPos(uri: String, pos: Long, dur: Long) {
        if (pos > 5000 && (dur <= 0 || pos < dur - 8000)) {
            vpos[uri] = pos
            vlast[uri] = System.currentTimeMillis()
            vpSp.edit().putLong(uri, pos).apply()
            vtSp.edit().putLong(uri, System.currentTimeMillis()).apply()
            if (vpos.size > 300) {
                val old = vlast.entries.sortedBy { it.value }.take(100).map { it.key }
                val e1 = vpSp.edit(); val e2 = vtSp.edit()
                old.forEach { vpos.remove(it); vlast.remove(it); e1.remove(it); e2.remove(it) }
                e1.apply(); e2.apply()
            }
        } else clearVideoPos(uri)
    }

    fun clearVideoPos(uri: String) {
        if (vpos.remove(uri) != null) {
            vlast.remove(uri)
            vpSp.edit().remove(uri).apply(); vtSp.edit().remove(uri).apply()
        }
    }
}

// ═══════════════════════════ صور مصغّرة (فيديو + أغلفة ألبومات) ════════════════�[...]

object Thumbs {
    private val cache = LruCache<String, Bitmap>(160)
    private val failed = HashSet<String>()

    fun peek(uri: String, px: Int): Bitmap? = cache.get("$uri@$px")

    fun load(c: Context, uri: String, px: Int): Bitmap? {
        val key = "$uri@$px"
        cache.get(key)?.let { return it }
        if (synchronized(failed) { key in failed }) return null
        return try {
            c.contentResolver.loadThumbnail(Uri.parse(uri), Size(px, px), null).also { cache.put(key, it) }
        } catch (_: Throwable) {
            synchronized(failed) { failed.add(key) }
            null
        }
    }
}

// ═══════════════════════════ تنسيقات ═══════════════════════════

fun fmtMs(ms: Long): String {
    val s = (ms / 1000).toInt().coerceAtLeast(0)
    val h = s / 3600; val m = s % 3600 / 60; val x = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, x) else String.format(Locale.US, "%d:%02d", m, x)
}

fun fmtSize(b: Long): String = when {
    b >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", b / 1073741824.0)
    b >= 1L shl 20 -> String.format(Locale.US, "%.0f MB", b / 1048576.0)
    else -> String.format(Locale.US, "%d KB", b / 1024)
}

// الجودة الحقيقية = الضلع الأقصر (عشان الفيديو الطولي 1080x1920 يبقى 1080p)
fun qTag(w: Int, h: Int): String {
    val s = if (w > 0 && h > 0) minOf(w, h) else h
    return when {
        s >= 4320 -> "8K"; s >= 2160 -> "4K"; s >= 1440 -> "2K"; s >= 1080 -> "Full HD"
        s >= 720 -> "HD"; s >= 480 -> "480p"; s > 0 -> "${s}p"; else -> ""
    }
}

// قايمة فيديوهات المشغّل بتتمرّر من المكتبة لـ VideoActivity من غير Intent ضخم
object VQ {
    var items: List<Vid> = emptyList()
    var start: Int = 0
}
