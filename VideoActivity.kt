@file:OptIn(ExperimentalMaterial3Api::class)

package app.flowplay

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color as AColor
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class TrackOpt(val group: Tracks.Group, val idx: Int, val name: String, val selected: Boolean)

// كل حالة الواجهة في مكان واحد
class VState {
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(true)
    var ended by mutableStateOf(false)
    var pos by mutableLongStateOf(0L)
    var dur by mutableLongStateOf(0L)
    var title by mutableStateOf("")
    var index by mutableIntStateOf(0)
    var count by mutableIntStateOf(1)
    var speed by mutableFloatStateOf(1f)
    var aspect by mutableIntStateOf(Prefs.aspect)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var locked by mutableStateOf(false)
    var lockShown by mutableStateOf(false)
    var controls by mutableStateOf(true)
    var touch by mutableIntStateOf(0)
    var pip by mutableStateOf(false)
    var bgAudio by mutableStateOf(false)         // تشغيل صوت فقط في الخلفية
    var orient by mutableIntStateOf(0)
    var panel by mutableIntStateOf(0)            // 0 مفيش، 1 سرعة، 2 مسارات، 3 القائمة، 4 المزيد، 5 معلومات
    var boost by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var vw by mutableIntStateOf(0)
    var vh by mutableIntStateOf(0)
    var audioTracks by mutableStateOf<List<TrackOpt>>(emptyList())
    var textTracks by mutableStateOf<List<TrackOpt>>(emptyList())
    var textOff by mutableStateOf(true)
    var sleepEnd by mutableLongStateOf(0L)       // وقت انتهاء مؤقت النوم (0 = مقفول)
    var abA by mutableLongStateOf(-1L)           // بداية مقطع التكرار
    var abB by mutableLongStateOf(-1L)           // نهاية مقطع التكرار
    var muted by mutableStateOf(false)
    var gain by mutableIntStateOf(0)             // تضخيم الصوت بالـ mB (0..1500)

    // HUD: 1 سطوع، 2 صوت، 3 تقديم
    var hudKind by mutableIntStateOf(0)
    var hudFrac by mutableFloatStateOf(0f)
    var hudText by mutableStateOf("")

    // الضغط المزدوج
    var flashSide by mutableIntStateOf(0)
    var flashSec by mutableIntStateOf(0)
    var flashKey by mutableIntStateOf(0)
    private var flashTime = 0L

    var toastText by mutableStateOf("")
    var toastKey by mutableIntStateOf(0)

    fun flash(side: Int) {
        val now = System.currentTimeMillis()
        if (side != flashSide || now - flashTime > 900) flashSec = 0
        flashSec += Prefs.seekSec; flashSide = side; flashTime = now; flashKey++
    }

    fun toast(t: String) { toastText = t; toastKey++ }
    fun hud(kind: Int, frac: Float, text: String) { hudKind = kind; hudFrac = frac; hudText = text }
}

class VideoActivity : ComponentActivity() {
    lateinit var player: ExoPlayer
    val vs = VState()
    var items: List<Vid> = emptyList()
    var brightness = 0.5f
    private lateinit var audio: AudioManager
    private var autoApplied = false
    var external = false          // اتفتح من تطبيق تاني (مش من جوه Next)
    private var enhancer: LoudnessEnhancer? = null

    private val subPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) addSubtitle(u)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppInit.ensure(this)
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val lp = window.attributes
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = lp
        if (Prefs.keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideBars()

        val b = window.attributes.screenBrightness
        brightness = if (b >= 0f) b else try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (_: Throwable) { 0.5f }

        buildPlayer()
        handleIntent(intent)
        setContent { AppTheme(forceDark = true) { VideoUi(this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        savePos()
        handleIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    private fun hideBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    // ─── المشغّل ───
    private fun buildPlayer() {
        val rf = DefaultRenderersFactory(this).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        player = ExoPlayer.Builder(this, rf)
            .setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.pauseAtEndOfMediaItems = !Prefs.autoNext
        attachEnhancer()
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) { attachEnhancer() }

            override fun onEvents(p: Player, e: Player.Events) {
                vs.playing = p.playWhenReady && p.playbackState != Player.STATE_ENDED && p.playbackState != Player.STATE_IDLE
                vs.buffering = p.playbackState == Player.STATE_BUFFERING
                vs.ended = p.playbackState == Player.STATE_ENDED
                vs.index = p.currentMediaItemIndex
                vs.count = p.mediaItemCount
                vs.title = items.getOrNull(p.currentMediaItemIndex)?.title ?: ""
                vs.repeat = p.repeatMode
                if (!vs.boost) vs.speed = p.playbackParameters.speed
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) player.currentMediaItem?.mediaId?.let { Store.clearVideoPos(it) }
                if (state == Player.STATE_READY) vs.error = null
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                vs.vw = videoSize.width; vs.vh = videoSize.height
                if (Prefs.openOrient == 0 && !autoApplied && vs.vw > 0) {
                    autoApplied = true
                    setOrient(if (vs.vw >= vs.vh) 1 else 2)
                }
            }

            override fun onTracksChanged(tracks: Tracks) { updateTracks(tracks) }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId ?: return
                vs.error = null
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
                autoApplied = false
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && Prefs.resume) {
                    val p = Store.videoPos(id)
                    if (p > 0 && player.currentPosition < 1500) player.seekTo(p)
                }
            }

            override fun onPositionDiscontinuity(o: Player.PositionInfo, n: Player.PositionInfo, reason: Int) {
                if (o.mediaItemIndex != n.mediaItemIndex) {
                    val id = o.mediaItem?.mediaId ?: return
                    if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) Store.clearVideoPos(id)
                    else Store.setVideoPos(id, o.positionMs, items.firstOrNull { it.uri == id }?.dur ?: 0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                vs.error = "تعذّر تشغيل الملف ده (${error.errorCodeName})"
                vs.controls = true
            }
        })
    }

    private fun nameOf(u: Uri): String {
        if (u.scheme == "content") try {
            contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) return it.getString(0).substringBeforeLast('.')
            }
        } catch (_: Throwable) {}
        return (u.lastPathSegment ?: u.toString()).substringAfterLast('/').substringBeforeLast('.')
    }

    private fun handleIntent(i: Intent?) {
        var list: List<Vid>
        var start: Int
        external = false
        if (i?.getBooleanExtra("q", false) == true && VQ.items.isNotEmpty()) {
            list = VQ.items; start = VQ.start
        } else {
            val u = i?.data
            if (u == null) { finish(); return }
            external = true
            list = listOf(Vid(-1, u.toString(), nameOf(u), 0, 0, 0, 0, 0, ""))
            start = 0
        }
        items = list
        autoApplied = false
        // اتجاه الشاشة عند الفتح: 1 طولي، 2 عرضي، 0 حسب الفيديو (بيتحدد أول ما يتعرف على الأبعاد)
        when (Prefs.openOrient) { 1 -> setOrient(2); 2 -> setOrient(1); else -> {} }
        val mis = list.map {
            MediaItem.Builder().setMediaId(it.uri).setUri(it.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).build()).build()
        }
        val pos = if (Prefs.resume) Store.videoPos(list[start].uri) else 0L
        player.setMediaItems(mis, start, pos)
        player.setPlaybackSpeed(Prefs.defaultSpeed)
        vs.speed = Prefs.defaultSpeed
        player.prepare()
        player.playWhenReady = true
    }

    // ─── مسارات الصوت والترجمة ───
    private fun trackName(f: Format, i: Int, isAudio: Boolean): String {
        val lang = f.language?.takeIf { it != "und" && it.isNotBlank() }
            ?.let { l -> Locale.forLanguageTag(l).getDisplayLanguage(Locale("ar")).ifBlank { l } }
        val base = f.label?.takeIf { it.isNotBlank() } ?: lang ?: (if (isAudio) "صوت ${i + 1}" else "ترجمة ${i + 1}")
        if (!isAudio) return base
        val extra = listOfNotNull(
            f.sampleMimeType?.substringAfter('/')?.uppercase()?.takeIf { it.isNotBlank() },
            if (f.channelCount > 2) "${f.channelCount}ch" else null
        ).joinToString(" · ")
        return if (extra.isEmpty()) base else "$base · $extra"
    }

    private fun updateTracks(t: Tracks) {
        val a = ArrayList<TrackOpt>(); val s = ArrayList<TrackOpt>()
        for (g in t.groups) {
            val type = g.type
            if (type != C.TRACK_TYPE_AUDIO && type != C.TRACK_TYPE_TEXT) continue
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                val isAudio = type == C.TRACK_TYPE_AUDIO
                val list = if (isAudio) a else s
                list.add(TrackOpt(g, i, trackName(g.getTrackFormat(i), list.size, isAudio), g.isTrackSelected(i)))
            }
        }
        vs.audioTracks = a; vs.textTracks = s
        vs.textOff = s.none { it.selected }
    }

    fun selectTrack(o: TrackOpt) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(o.group.type, false)
            .setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.idx)).build()
    }

    fun disableSubtitles() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
    }

    fun pickSubtitle() { try { subPicker.launch(arrayOf("*/*")) } catch (_: Throwable) {} }

    private fun addSubtitle(u: Uri) {
        try {
            val name = nameOf(u)
            val full = (u.lastPathSegment ?: "").lowercase()
            val mime = when {
                full.endsWith(".vtt") -> MimeTypes.TEXT_VTT
                full.endsWith(".ass") || full.endsWith(".ssa") -> MimeTypes.TEXT_SSA
                full.endsWith(".ttml") || full.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            val cur = player.currentMediaItem ?: return
            val idx = player.currentMediaItemIndex
            val pos = player.currentPosition
            val sub = MediaItem.SubtitleConfiguration.Builder(u).setMimeType(mime).setLabel(name)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
            player.replaceMediaItem(idx, cur.buildUpon().setSubtitleConfigurations(listOf(sub)).build())
            player.seekTo(idx, pos)
            vs.toast("تم تحميل الترجمة")
        } catch (_: Throwable) { vs.toast("تعذّر تحميل الترجمة") }
    }

    // ─── التحكم ───
    fun togglePlay() {
        if (vs.ended) { player.seekTo(0); player.play() }
        else if (player.playWhenReady) player.pause() else { if (player.playbackState == Player.STATE_IDLE) player.prepare(); player.play() }
    }

    fun seekBy(ms: Long) {
        val d = if (player.duration > 0) player.duration else Long.MAX_VALUE
        player.seekTo((player.currentPosition + ms).coerceIn(0L, d))
    }

    fun seekTo(ms: Long) { player.seekTo(ms.coerceAtLeast(0L)) }

    // ─── مميزات إضافية ───
    private fun attachEnhancer() {
        try { enhancer?.release() } catch (_: Throwable) {}
        enhancer = try {
            LoudnessEnhancer(player.audioSessionId).also {
                it.setTargetGain(vs.gain)
                it.setEnabled(vs.gain > 0)
            }
        } catch (_: Throwable) { null }
    }

    fun setGain(mb: Int) {
        vs.gain = mb.coerceIn(0, 1500)
        try {
            val e = enhancer
            if (e != null) { e.setTargetGain(vs.gain); e.setEnabled(vs.gain > 0) }
        } catch (_: Throwable) {}
    }

    fun toggleMute() {
        vs.muted = !vs.muted
        player.volume = if (vs.muted) 0f else 1f
        vs.toast(if (vs.muted) "الصوت مكتوم" else "الصوت شغّال")
    }

    fun skipIntro() {
        seekBy(Prefs.skipIntro * 1000L)
        vs.toast("تخطي ${Prefs.skipIntro} ثانية")
    }

    fun setSleep(min: Int) {
        vs.sleepEnd = if (min <= 0) 0L else System.currentTimeMillis() + min * 60_000L
        vs.toast(if (min <= 0) "تم إيقاف المؤقت" else "هيقف بعد $min دقيقة")
    }

    fun markA() { vs.abA = player.currentPosition; vs.abB = -1L; vs.toast("تم تحديد البداية A") }

    fun markB() {
        if (vs.abA < 0 || player.currentPosition <= vs.abA + 500) { vs.toast("حدّد A الأول"); return }
        vs.abB = player.currentPosition
        vs.toast("بيكرر المقطع A-B")
    }

    fun clearAB() { vs.abA = -1L; vs.abB = -1L; vs.toast("تم مسح التكرار") }

    fun toggleFavNow() {
        val id = player.currentMediaItem?.mediaId ?: return
        Store.toggleVFav(id)
        vs.toast(if (Store.isVFav(id)) "اتضاف للمفضلة" else "اتشال من المفضلة")
    }

    fun infoLines(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val cur = items.getOrNull(player.currentMediaItemIndex)
        out.add("الاسم" to (cur?.title ?: vs.title))
        if (vs.dur > 0) out.add("المدة" to fmtMs(vs.dur))
        if (cur != null && cur.size > 0) out.add("الحجم" to fmtSize(cur.size))
        if (cur != null && cur.folder.isNotBlank()) out.add("المجلد" to cur.folder)
        val v = player.videoFormat
        if (v != null) {
            if (v.width > 0) out.add("الأبعاد" to "${v.width} × ${v.height}  (${qTag(v.width, v.height)})")
            v.sampleMimeType?.let { out.add("ترميز الفيديو" to it.substringAfter('/').uppercase()) }
            if (v.frameRate > 0f) out.add("معدل الإطارات" to String.format(Locale.US, "%.0f fps", v.frameRate))
            if (v.bitrate > 0) out.add("معدل البيانات" to String.format(Locale.US, "%.1f Mbps", v.bitrate / 1_000_000f))
        }
        val a = player.audioFormat
        if (a != null) {
            a.sampleMimeType?.let { out.add("ترميز الصوت" to it.substringAfter('/').uppercase()) }
            if (a.channelCount > 0) out.add("القنوات" to a.channelCount.toString())
            if (a.sampleRate > 0) out.add("التردد" to "${a.sampleRate} Hz")
        }
        return out
    }

    fun setSpeed(s: Float) {
        val v = ((s * 20).roundToInt() / 20f).coerceIn(0.25f, 3f)
        vs.speed = v; player.setPlaybackSpeed(v)
    }

    fun startBoost() { vs.boost = true; player.setPlaybackSpeed(2f) }
    fun endBoost() { vs.boost = false; player.setPlaybackSpeed(vs.speed) }

    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        vs.toast(when (player.repeatMode) { Player.REPEAT_MODE_ALL -> "تكرار الكل"; Player.REPEAT_MODE_ONE -> "تكرار الفيديو"; else -> "بدون تكرار" })
    }

    fun cycleAspect() {
        vs.aspect = (vs.aspect + 1) % 3
        vs.toast(listOf("مناسب للشاشة", "ملء الشاشة", "قص وتكبير")[vs.aspect])
    }

    fun setOrient(o: Int) {
        vs.orient = o
        requestedOrientation = when (o) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun cycleOrient() {
        setOrient((vs.orient + 1) % 3)
        vs.toast(listOf("تدوير تلقائي", "أفقي", "طولي")[vs.orient])
    }

    fun applyBrightness(b: Float) {
        brightness = b.coerceIn(0.02f, 1f)
        val lp = window.attributes; lp.screenBrightness = brightness; window.attributes = lp
    }

    fun volFrac(): Float = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    fun setVolume(f: Float) {
        val mx = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (f * mx).roundToInt().coerceIn(0, mx), 0)
    }

    // فتح التطبيق الكامل من الفيديو المفتوح خارجيًا: الفيديو بيكمّل في نافذة عايمة فوق التطبيق
    fun openApp() {
        if (Build.VERSION.SDK_INT >= 26) enterPip() else vs.bgAudio = true
        try { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT < 26) return
        try {
            val w = max(vs.vw, 16); val h = max(vs.vh, 9)
            val ratio = w.toFloat() / h
            val r = when {
                ratio > 2.39f -> Rational(239, 100)
                ratio < 0.42f -> Rational(42, 100)
                else -> Rational(w, h)
            }
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(r).build())
        } catch (_: Throwable) {}
    }

    fun savePos() {
        if (!::player.isInitialized) return
        val id = player.currentMediaItem?.mediaId ?: return
        val d = if (player.duration > 0) player.duration else 0L
        Store.setVideoPos(id, player.currentPosition, d)
    }

    // ─── دورة الحياة ───
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Prefs.pipAuto && player.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        vs.pip = isInPictureInPictureMode
        if (isInPictureInPictureMode) { vs.controls = false; vs.panel = 0 }
    }

    override fun onPause() {
        super.onPause()
        savePos()
    }

    override fun onStart() {
        super.onStart()
        if (::player.isInitialized) setVideoOff(false)
    }

    fun setVideoOff(off: Boolean) {
        try {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, off).build()
        } catch (_: Throwable) {}
    }

    fun toggleBgAudio() { vs.bgAudio = !vs.bgAudio }

    override fun onStop() {
        super.onStop()
        savePos()
        if (!isInPictureInPictureMode) {
            if (vs.bgAudio) setVideoOff(true) else player.pause()
        }
    }

    override fun onDestroy() {
        savePos()
        try { enhancer?.release() } catch (_: Throwable) {}
        player.release()
        super.onDestroy()
    }
}

// ═══════════════════════════ واجهة المشغّل ═══════════════════════════

@Composable
private fun CtlBtn(icon: ImageVector, on: () -> Unit, active: Boolean = false, size: Int = 44, desc: String? = null, glass: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.86f else 1f, spring(), label = "cb")
    Box(
        Modifier.size(size.dp).scale(sc).clip(CircleShape)
            .background(
                when {
                    active -> cs.primary.copy(alpha = 0.24f)
                    glass -> Color.White.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
            )
            .clickable(interactionSource = src, indication = null, onClick = on),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, Modifier.size((size * 0.5f).dp), tint = if (active) cs.primary else Color.White)
    }
}

// زر السابق / التالي: دايرة زجاجية بإطار خفيف
@Composable
private fun SkipBtn(icon: ImageVector, enabled: Boolean, desc: String, on: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.88f else 1f, spring(), label = "sk")
    Box(
        Modifier.size(56.dp).scale(sc).clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.16f else 0.07f))
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.22f else 0.08f), CircleShape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = on),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, Modifier.size(28.dp), tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f))
    }
}

// زر التشغيل: كبير، بيتحوّل من دايرة لمربّع مدوّر لما الفيديو يشتغل، وله توهّج بلون التطبيق
@Composable
private fun PlayBtn(playing: Boolean, ended: Boolean, loading: Boolean, on: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.92f else 1f, spring(), label = "pl")
    val r by animateDpAsState(if (playing) 26.dp else 40.dp, spring(), label = "pr")
    val shape = RoundedCornerShape(r)
    Box(
        Modifier.size(80.dp).scale(sc)
            .shadow(18.dp, shape, ambientColor = cs.primary, spotColor = cs.primary)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(cs.primary, cs.primary.copy(alpha = 0.85f))))
            .clickable(interactionSource = src, indication = null, onClick = on),
        contentAlignment = Alignment.Center
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(66.dp), color = cs.onPrimary.copy(alpha = 0.5f), strokeWidth = 3.dp)
        Icon(
            if (ended) PIc.Replay else if (playing) PIc.Pause else PIc.Play, null,
            Modifier.size(38.dp), tint = cs.onPrimary
        )
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (selected) cs.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge, color = if (selected) cs.onPrimaryContainer else cs.onSurface
        )
        if (selected) Icon(Ic.Check, null, Modifier.size(20.dp), tint = cs.onPrimaryContainer)
    }
}

@Composable
private fun PanelFrame(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxHeight().widthIn(max = 360.dp).fillMaxWidth(0.86f)
            .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)).background(cs.surfaceContainer)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .safeDrawingPadding().padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleLarge)
            IconButton(onClose) { Icon(Ic.Close, "إغلاق") }
        }
        content()
    }
}

@Composable
fun VideoUi(a: VideoActivity) {
    val vs = a.vs
    val cs = MaterialTheme.colorScheme
    val player = a.player

    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            vs.pos = player.currentPosition
            vs.dur = if (player.duration > 0) player.duration else 0L
            if (vs.abA >= 0 && vs.abB > vs.abA && vs.pos >= vs.abB) a.seekTo(vs.abA)
            if (++n % 25 == 0) a.savePos()
            delay(200)
        }
    }
    LaunchedEffect(vs.controls, vs.playing, vs.touch, vs.panel) {
        if (vs.controls && vs.playing && vs.panel == 0) { delay(3500); vs.controls = false }
    }
    LaunchedEffect(vs.sleepEnd) {
        if (vs.sleepEnd > 0L) {
            val left = vs.sleepEnd - System.currentTimeMillis()
            if (left > 0) delay(left)
            if (vs.sleepEnd > 0L) { player.pause(); vs.sleepEnd = 0L; vs.toast("وقف التشغيل (مؤقت النوم)") }
        }
    }
    LaunchedEffect(vs.lockShown, vs.touch) { if (vs.lockShown) { delay(2500); vs.lockShown = false } }
    LaunchedEffect(vs.flashKey) { if (vs.flashKey > 0) { delay(700); vs.flashSide = 0 } }
    LaunchedEffect(vs.toastKey) { if (vs.toastKey > 0) { delay(1100); vs.toastText = "" } }

    // الواجهة كلها LTR (زي أي مشغّل فيديو): شريط التقدّم من الشمال لليمين
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {

            AndroidView(
                factory = { ctx ->
                    val pv = PlayerView(ctx)
                    pv.useController = false
                    pv.setKeepContentOnPlayerReset(true)
                    pv.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    pv.player = player
                    pv.subtitleView?.setStyle(
                        CaptionStyleCompat(AColor.WHITE, AColor.TRANSPARENT, AColor.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, AColor.BLACK, null)
                    )
                    pv
                },
                update = { pv ->
                    pv.resizeMode = when (vs.aspect) {
                        1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    pv.subtitleView?.setFractionalTextSize(when (Prefs.subSize) { 0 -> 0.04f; 2 -> 0.07f; else -> 0.0533f })
                    pv.subtitleView?.setStyle(
                        CaptionStyleCompat(
                            when (Prefs.subColor) { 1 -> AColor.YELLOW; 2 -> AColor.CYAN; else -> AColor.WHITE },
                            if (Prefs.subBg) AColor.argb(170, 0, 0, 0) else AColor.TRANSPARENT,
                            AColor.TRANSPARENT,
                            if (Prefs.subBg) CaptionStyleCompat.EDGE_TYPE_NONE else CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            AColor.BLACK, null
                        )
                    )
                },
                modifier = Modifier.fillMaxSize()
            )

            // ─── طبقة اللمس: ضغطة / ضغطتين / ضغطة مطوّلة ───
            Box(
                Modifier.fillMaxSize().pointerInput(vs.locked) {
                    detectTapGestures(
                        onPress = { tryAwaitRelease(); if (vs.boost) a.endBoost() },
                        onLongPress = { if (!vs.locked && Prefs.longSpeed && !vs.pip) a.startBoost() },
                        onDoubleTap = { o ->
                            if (!vs.locked) {
                                val third = size.width / 3f
                                if (Prefs.dtCenter && o.x > third && o.x < third * 2f) {
                                    a.togglePlay()
                                } else {
                                    val left = o.x < size.width / 2f
                                    a.seekBy(if (left) -Prefs.seekSec * 1000L else Prefs.seekSec * 1000L)
                                    vs.flash(if (left) -1 else 1)
                                }
                            }
                        },
                        onTap = {
                            if (vs.locked) { vs.lockShown = !vs.lockShown; vs.touch++ }
                            else { vs.controls = !vs.controls; vs.touch++ }
                        }
                    )
                }
                // ─── السحب: أفقي = تقديم، يسار = سطوع، يمين = صوت ───
                .pointerInput(vs.locked, Prefs.gestures) {
                    if (vs.locked || !Prefs.gestures) return@pointerInput
                    var mode = 0
                    var accX = 0f; var accY = 0f
                    var startLeft = true
                    var seekBase = 0L; var seekDelta = 0f; var target = 0L
                    var volF = 0f; var briF = 0f
                    detectDragGestures(
                        onDragStart = { o ->
                            mode = 0; accX = 0f; accY = 0f; startLeft = o.x < size.width / 2f
                            seekBase = player.currentPosition; seekDelta = 0f; target = seekBase
                            volF = a.volFrac(); briF = a.brightness
                        },
                        onDragEnd = { if (mode == 1) a.seekTo(target); mode = 0; vs.hudKind = 0 },
                        onDragCancel = { mode = 0; vs.hudKind = 0 },
                        onDrag = { ch, d ->
                            ch.consume()
                            if (mode == 0) {
                                accX += d.x; accY += d.y
                                if (abs(accX) > 24f || abs(accY) > 24f)
                                    mode = if (abs(accX) > abs(accY)) 1 else if (startLeft) 2 else 3
                            } else when (mode) {
                                1 -> {
                                    seekDelta += d.x / size.width * 120_000f
                                    val mx = if (vs.dur > 0) vs.dur else Long.MAX_VALUE
                                    target = (seekBase + seekDelta.toLong()).coerceIn(0L, mx)
                                    val diff = (target - seekBase) / 1000
                                    vs.hud(3, 0f, (if (diff >= 0) "+" else "-") + abs(diff) + "ث  •  " + fmtMs(target))
                                }
                                2 -> {
                                    briF = (briF - d.y / size.height * 1.3f).coerceIn(0.02f, 1f)
                                    a.applyBrightness(briF)
                                    vs.hud(1, briF, "${(briF * 100).roundToInt()}%")
                                }
                                3 -> {
                                    volF = (volF - d.y / size.height * 1.3f).coerceIn(0f, 1f)
                                    a.setVolume(volF)
                                    vs.hud(2, volF, "${(volF * 100).roundToInt()}%")
                                }
                            }
                        }
                    )
                }
            )

            if (!vs.pip) {
                // ─── مؤشرات: الضغط المزدوج / HUD / رسالة / سرعة 2x ───
                AnimatedVisibility(vs.flashSide != 0, Modifier.align(if (vs.flashSide < 0) Alignment.CenterStart else Alignment.CenterEnd), enter = fadeIn(), exit = fadeOut()) {
                    Column(
                        Modifier.padding(horizontal = 56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 22.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(if (vs.flashSide < 0) Ic.Backward else Ic.Forward, null, Modifier.size(28.dp), tint = Color.White)
                        Text("${vs.flashSec} ث", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    }
                }
                AnimatedVisibility(vs.hudKind != 0, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        Modifier.clip(RoundedCornerShape(28.dp)).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (vs.hudKind != 3) {
                            Icon(if (vs.hudKind == 1) Ic.Bright else Ic.Volume, null, Modifier.size(24.dp), tint = Color.White)
                            Box(Modifier.width(110.dp).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                                Box(Modifier.fillMaxWidth(vs.hudFrac.coerceIn(0f, 1f)).fillMaxHeight().background(cs.primary))
                            }
                        }
                        Text(vs.hudText, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
                AnimatedVisibility(vs.toastText.isNotEmpty(), Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 64.dp), enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        vs.toastText, color = Color.White, style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
                AnimatedVisibility(vs.boost, Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 20.dp), enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Ic.Forward, null, Modifier.size(18.dp), tint = Color.White)
                        Text("2x", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (vs.buffering && !vs.controls && vs.error == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center).size(48.dp), color = Color.White)
                }

                // ─── خطأ ───
                vs.error?.let { err ->
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(28.dp))
                            .background(Color.Black.copy(alpha = 0.75f)).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(err, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button({ vs.error = null; player.prepare(); player.play() }) { Text("إعادة المحاولة") }
                            if (player.hasNextMediaItem()) FilledTonalButton({ vs.error = null; player.seekToNextMediaItem(); player.prepare(); player.play() }) { Text("التالي") }
                        }
                    }
                }

                // ─── القفل ───
                AnimatedVisibility(vs.locked && vs.lockShown, Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(start = 24.dp), enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        Modifier.clip(CircleShape).background(cs.primary).clickable { vs.locked = false; vs.lockShown = false; vs.controls = true; vs.touch++ }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Ic.Unlock, null, Modifier.size(22.dp), tint = cs.onPrimary)
                        Text("فتح القفل", color = cs.onPrimary, style = MaterialTheme.typography.titleSmall)
                    }
                }

                // ─── أزرار التحكم ───
                AnimatedVisibility(vs.controls && !vs.locked, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                            .safeDrawingPadding()
                    ) {
                        // الشريط العلوي
                        Row(
                            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CtlBtn(Ic.Back, { a.finish() }, size = 40, desc = "رجوع")
                            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                                Text(vs.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (vs.count > 1) Text("${vs.index + 1} / ${vs.count}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                            }
                            if (vs.count > 1) CtlBtn(Ic.Queue, { vs.panel = 3 }, size = 40, desc = "القائمة")
                            CtlBtn(Ic.Headphones, { a.toggleBgAudio() }, active = vs.bgAudio, size = 40, desc = "تشغيل صوت فقط")
                            if (Build.VERSION.SDK_INT >= 26) CtlBtn(Ic.Pip, { a.enterPip() }, size = 40, desc = "نافذة عايمة")
                            CtlBtn(Ic.Subs, { vs.panel = 2 }, active = !vs.textOff, size = 40, desc = "الصوت والترجمة")
                            CtlBtn(Ic.More, { vs.panel = 4 }, active = vs.abB > 0 || vs.sleepEnd > 0 || vs.gain > 0 || vs.muted, size = 40, desc = "المزيد")
                        }

                        // أزرار الوسط
                        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            SkipBtn(PIc.Prev, vs.count > 1 || vs.pos > 3000, "السابق") { player.seekToPrevious() }
                            PlayBtn(vs.playing, vs.ended, vs.buffering && vs.error == null) { a.togglePlay() }
                            SkipBtn(PIc.Next, player.hasNextMediaItem(), "التالي") { player.seekToNextMediaItem() }
                        }

                        // الشريط السفلي
                        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            var scrub by remember { mutableStateOf<Float?>(null) }
                            val frac = if (vs.dur > 0) vs.pos.toFloat() / vs.dur else 0f
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(fmtMs(((scrub ?: frac) * vs.dur).toLong()), color = Color.White, style = MaterialTheme.typography.labelLarge)
                                WavySeek(
                                    frac, vs.playing, Modifier.weight(1f), wave = false,
                                    onScrub = { scrub = it; vs.touch++ },
                                    onSeek = { f -> if (vs.dur > 0) a.seekTo((f * vs.dur).toLong()) }
                                )
                                Text(if (vs.dur > 0) fmtMs(vs.dur) else "--:--", color = Color.White, style = MaterialTheme.typography.labelLarge)
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
                            ) {
                                CtlBtn(Ic.Lock, { vs.locked = true; vs.controls = false; vs.lockShown = true; vs.touch++ }, glass = false, desc = "قفل")
                                CtlBtn(Ic.Aspect, { a.cycleAspect() }, glass = false, desc = "حجم الصورة")
                                CtlBtn(Ic.Rotate, { a.cycleOrient() }, active = vs.orient != 0, glass = false, desc = "تدوير")
                                CtlBtn(if (vs.repeat == Player.REPEAT_MODE_ONE) Ic.RepeatOne else Ic.Repeat, { a.cycleRepeat() }, active = vs.repeat != Player.REPEAT_MODE_OFF, glass = false, desc = "تكرار")
                                val spTxt = (if (vs.speed % 1f == 0f) vs.speed.toInt().toString() else String.format(Locale.US, "%.2f", vs.speed).trimEnd('0')) + "x"
                                Box(
                                    Modifier.height(36.dp).clip(CircleShape)
                                        .background(if (vs.speed != 1f) cs.primary.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.14f))
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vs.panel = 1 }
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(spTxt, color = if (vs.speed != 1f) cs.primary else Color.White, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }

                // ─── اللوحات الجانبية ───
                AnimatedVisibility(vs.panel != 0, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vs.panel = 0 }
                    )
                }
                AnimatedVisibility(
                    vs.panel != 0, Modifier.align(Alignment.CenterEnd),
                    enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }
                ) {
                    when (vs.panel) {
                        1 -> PanelFrame("سرعة التشغيل", { vs.panel = 0 }) {
                            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(
                                    String.format(Locale.US, "%.2fx", vs.speed), Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.displaySmall, color = cs.primary, textAlign = TextAlign.Center
                                )
                                Slider(vs.speed, { a.setSpeed(it) }, valueRange = 0.25f..3f)
                                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f).chunked(4).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { s ->
                                            FilterChip(
                                                vs.speed == s, { a.setSpeed(s) },
                                                { Text(if (s % 1f == 0f) "${s.toInt()}x" else "${s}x", maxLines = 1) },
                                                Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                                TextButton({ a.setSpeed(1f) }, Modifier.fillMaxWidth()) { Text("رجوع للسرعة العادية") }
                            }
                        }
                        2 -> PanelFrame("الصوت والترجمة", { vs.panel = 0 }) {
                            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (vs.audioTracks.isNotEmpty()) {
                                    Text("المسار الصوتي", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(8.dp))
                                    vs.audioTracks.forEach { t -> OptionRow(t.name, t.selected) { a.selectTrack(t) } }
                                }
                                Text("الترجمة", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 8.dp, end = 8.dp))
                                OptionRow("بدون ترجمة", vs.textOff) { a.disableSubtitles() }
                                vs.textTracks.forEach { t -> OptionRow(t.name, t.selected) { a.selectTrack(t) } }
                                Spacer(Modifier.height(8.dp))
                                FilledTonalButton({ a.pickSubtitle() }, Modifier.fillMaxWidth()) {
                                    Icon(Ic.Subs, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("تحميل ترجمة من ملف")
                                }
                                Footnote("بيدعم SRT و VTT و ASS.")
                            }
                        }
                        3 -> PanelFrame("قائمة التشغيل", { vs.panel = 0 }) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed(a.items) { i, v ->
                                    OptionRow(v.title, i == vs.index) {
                                        player.seekToDefaultPosition(i); player.play(); vs.panel = 0
                                    }
                                }
                            }
                        }
                        4 -> PanelFrame("المزيد", { vs.panel = 0 }) {
                            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val curId = player.currentMediaItem?.mediaId ?: ""
                                val fav = Store.isVFav(curId)
                                OptionRow(if (fav) "إزالة من المفضلة" else "إضافة للمفضلة", fav) { a.toggleFavNow() }
                                OptionRow(if (vs.muted) "إلغاء كتم الصوت" else "كتم الصوت", vs.muted) { a.toggleMute() }
                                OptionRow("تخطي ${Prefs.skipIntro} ثانية", false) { vs.panel = 0; a.skipIntro() }
                                if (Build.VERSION.SDK_INT >= 26) OptionRow("نافذة عايمة", false) { vs.panel = 0; a.enterPip() }
                                OptionRow(if (vs.bgAudio) "إيقاف التشغيل كصوت فقط" else "تشغيل كصوت فقط (يكمل في الخلفية)", vs.bgAudio) { a.toggleBgAudio() }
                                if (a.external) OptionRow("فتح التطبيق (الفيديو يكمّل في نافذة)", false) { vs.panel = 0; a.openApp() }
                                OptionRow("معلومات الفيديو", false) { vs.panel = 5 }

                                Text("تضخيم الصوت", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(start = 8.dp, top = 14.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Slider(vs.gain.toFloat(), { a.setGain(it.roundToInt()) }, Modifier.weight(1f), valueRange = 0f..1500f)
                                    Text("+${vs.gain / 100} dB", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                                }

                                Text("تكرار مقطع (A-B)", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(start = 8.dp, top = 14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(vs.abA >= 0, { a.markA() }, { Text("A", maxLines = 1) }, Modifier.weight(1f))
                                    FilterChip(vs.abB > 0, { a.markB() }, { Text("B", maxLines = 1) }, Modifier.weight(1f))
                                    FilterChip(false, { a.clearAB() }, { Text("مسح", maxLines = 1) }, Modifier.weight(1f))
                                }
                                if (vs.abA >= 0) Text(
                                    "A: " + fmtMs(vs.abA) + (if (vs.abB > 0) "   B: " + fmtMs(vs.abB) else ""),
                                    color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp)
                                )

                                Text("مؤقت النوم", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(start = 8.dp, top = 14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(0, 15, 30, 60).forEach { m ->
                                        FilterChip(
                                            (m == 0 && vs.sleepEnd == 0L), { a.setSleep(m) },
                                            { Text(if (m == 0) "إيقاف" else "$m د", maxLines = 1) }, Modifier.weight(1f)
                                        )
                                    }
                                }
                                if (vs.sleepEnd > 0L) Text(
                                    "هيقف بعد حوالي " + ((vs.sleepEnd - System.currentTimeMillis()) / 60000 + 1) + " دقيقة",
                                    color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                        5 -> PanelFrame("معلومات الفيديو", { vs.panel = 0 }) {
                            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                a.infoLines().forEach { (k, v) ->
                                    Column(Modifier.padding(horizontal = 8.dp)) {
                                        Text(k, style = MaterialTheme.typography.labelMedium, color = cs.primary)
                                        Text(v, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                                    }
                                }
                                TextButton({ vs.panel = 4 }, Modifier.fillMaxWidth()) { Text("رجوع") }
                            }
                        }
                        else -> Spacer(Modifier.size(1.dp))
                    }
                }
            }
        }
    }
}
