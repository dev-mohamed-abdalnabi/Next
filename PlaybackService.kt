package app.flowplay

import android.app.PendingIntent
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// ═══════════════════════════ الخدمة: المشغّل عايش هنا فالموسيقى بتكمّل في الخلفية ═══════════════════════════

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    companion object {
        // نفس العملية: الواجهة بتتحكم في الخصائص اللي مش موجودة في MediaController (زي تخطي الصمت)
        var exo: ExoPlayer? = null
    }

    override fun onCreate() {
        super.onCreate()
        AppInit.ensure(this)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.skipSilenceEnabled = Prefs.skipSilence
        exo = player
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) { Fx.attach(audioSessionId) }
        })
        Fx.attach(player.audioSessionId)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        exo = null
        Fx.release()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

// ═══════════════════════════ المعادل الصوتي (Equalizer + Bass + Virtualizer) ═══════════════════════════

object Fx {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null

    var available by mutableStateOf(false)
    var bandCount by mutableIntStateOf(0)
    var minLevel by mutableIntStateOf(-1500)
    var maxLevel by mutableIntStateOf(1500)
    var freqs by mutableStateOf<List<Int>>(emptyList())      // Hz
    var levels by mutableStateOf<List<Int>>(emptyList())     // mB
    var presets by mutableStateOf<List<String>>(emptyList())
    var bassOk by mutableStateOf(false)
    var virtOk by mutableStateOf(false)

    fun attach(session: Int) {
        if (session <= 0) return
        release()
        try {
            val e = Equalizer(0, session)
            eq = e
            val n = e.numberOfBands.toInt()
            val r = e.bandLevelRange
            minLevel = r[0].toInt(); maxLevel = r[1].toInt()
            bandCount = n
            freqs = (0 until n).map { e.getCenterFreq(it.toShort()) / 1000 }
            presets = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
            val saved = Prefs.eqLevels.split(',').mapNotNull { it.toIntOrNull() }
            levels = (0 until n).map { i -> (saved.getOrNull(i) ?: e.getBandLevel(i.toShort()).toInt()).coerceIn(minLevel, maxLevel) }
            levels.forEachIndexed { i, l -> try { e.setBandLevel(i.toShort(), l.toShort()) } catch (_: Throwable) {} }
            e.setEnabled(Prefs.eqOn)
            available = true
        } catch (_: Throwable) { eq = null; available = false }
        try {
            val b = BassBoost(0, session)
            bass = b
            bassOk = b.strengthSupported
            if (bassOk) b.setStrength(Prefs.bassStr.toShort())
            b.setEnabled(Prefs.eqOn)
        } catch (_: Throwable) { bass = null; bassOk = false }
        try {
            val v = Virtualizer(0, session)
            virt = v
            virtOk = v.strengthSupported
            if (virtOk) v.setStrength(Prefs.virtStr.toShort())
            v.setEnabled(Prefs.eqOn)
        } catch (_: Throwable) { virt = null; virtOk = false }
    }

    fun release() {
        try { eq?.release() } catch (_: Throwable) {}
        try { bass?.release() } catch (_: Throwable) {}
        try { virt?.release() } catch (_: Throwable) {}
        eq = null; bass = null; virt = null
        available = false
    }

    fun setOn(on: Boolean) {
        Prefs.eqOn = on; Prefs.save()
        try { eq?.setEnabled(on) } catch (_: Throwable) {}
        try { bass?.setEnabled(on) } catch (_: Throwable) {}
        try { virt?.setEnabled(on) } catch (_: Throwable) {}
    }

    fun setBand(i: Int, level: Int) {
        if (i !in levels.indices) return
        levels = levels.toMutableList().also { it[i] = level }
        try { eq?.setBandLevel(i.toShort(), level.toShort()) } catch (_: Throwable) {}
        Prefs.eqLevels = levels.joinToString(","); Prefs.save()
    }

    fun usePreset(p: Int) {
        val e = eq ?: return
        try {
            e.usePreset(p.toShort())
            levels = (0 until bandCount).map { e.getBandLevel(it.toShort()).toInt() }
            Prefs.eqLevels = levels.joinToString(","); Prefs.save()
        } catch (_: Throwable) {}
    }

    fun reset() {
        levels = levels.map { 0 }
        levels.forEachIndexed { i, l -> try { eq?.setBandLevel(i.toShort(), l.toShort()) } catch (_: Throwable) {} }
        Prefs.eqLevels = levels.joinToString(","); Prefs.save()
    }

    fun setBass(v: Int) {
        Prefs.bassStr = v; Prefs.save()
        try { bass?.setStrength(v.toShort()) } catch (_: Throwable) {}
    }

    fun setVirt(v: Int) {
        Prefs.virtStr = v; Prefs.save()
        try { virt?.setStrength(v.toShort()) } catch (_: Throwable) {}
    }
}

// ═══════════════════════════ المتحكّم: حالة المشغّل كلها هنا والواجهة بتقراها ═══════════════════════════

object Music {
    private lateinit var app: Context
    private var ctl: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private val pending = ArrayList<(MediaController) -> Unit>()
    private var sleepJob: Job? = null
    private var counted = -2L

    var connected by mutableStateOf(false)
    var current by mutableStateOf<Song?>(null)
    var playing by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var speed by mutableFloatStateOf(1f)
    var duration by mutableLongStateOf(0L)
    var index by mutableIntStateOf(-1)
    var queue by mutableStateOf<List<Song>>(emptyList())
    var sleepEnd by mutableLongStateOf(0L)
    var sleepAtTrackEnd by mutableStateOf(false)

    val has: Boolean get() = current != null

    fun init(c: Context) { app = c.applicationContext }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player, events.contains(Player.EVENT_TIMELINE_CHANGED))
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null && sleepAtTrackEnd && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                sleepAtTrackEnd = false
                ctl?.pause()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val c = ctl ?: return
            if (isPlaying) {
                val id = c.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
                if (id > 0 && id != counted) { counted = id; Store.countPlay(id) }
            } else saveSession(c)
        }
    }

    fun connect() {
        if (ctl != null || future != null) return
        val f = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java)))
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    ctl = null; future = null; connected = false; playing = false
                }
            })
            .buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                ctl = c
                c.addListener(listener)
                connected = true
                sync(c, true)
                val run = ArrayList(pending); pending.clear()
                run.forEach { it(c) }
            } catch (_: Throwable) { future = null }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun withCtl(block: (MediaController) -> Unit) {
        val c = ctl
        if (c != null) block(c) else { pending.add(block); connect() }
    }

    private fun sync(p: Player, rebuild: Boolean) {
        playing = p.isPlaying
        shuffle = p.shuffleModeEnabled
        repeat = p.repeatMode
        speed = p.playbackParameters.speed
        index = p.currentMediaItemIndex
        current = p.currentMediaItem?.let { toSong(it) }
        duration = p.duration.takeIf { it > 0 } ?: current?.dur ?: 0L
        if (rebuild || queue.size != p.mediaItemCount) {
            queue = (0 until p.mediaItemCount).map { toSong(p.getMediaItemAt(it)) }
        }
    }

    private fun toSong(i: MediaItem): Song {
        val id = i.mediaId.toLongOrNull() ?: -1L
        if (id > 0) Media.byId[id]?.let { return it }
        val m = i.mediaMetadata
        val uri = if (id > 0) ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString() else i.mediaId
        return Song(
            id, uri, m.title?.toString() ?: "", m.artist?.toString() ?: "فنان غير معروف",
            m.albumTitle?.toString() ?: "", 0L, 0L, 0L, ""
        )
    }

    private fun saveSession(c: Player) {
        val n = c.mediaItemCount
        if (n == 0) return
        val ids = (0 until n).mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() }
        if (ids.size != n) return   // فيها ملف خارجي: ما نحفظش
        Prefs.lastQueue = ids.joinToString(",")
        Prefs.lastIndex = c.currentMediaItemIndex
        Prefs.lastPos = c.currentPosition
        Prefs.save()
    }

    private fun item(s: Song): MediaItem = MediaItem.Builder()
        .setMediaId(s.id.toString())
        .setUri(s.uri)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album)
                .setArtworkUri(Uri.parse("content://media/external/audio/albumart/" + s.albumId)).build()
        ).build()

    // ─── التشغيل ───
    fun play(list: List<Song>, start: Int, shuffleOn: Boolean = false) {
        if (list.isEmpty()) return
        withCtl { c ->
            c.setMediaItems(list.map { item(it) }, start.coerceIn(0, list.size - 1), 0L)
            c.shuffleModeEnabled = shuffleOn
            c.prepare()
            c.play()
        }
    }

    fun shuffleAll(list: List<Song>) {
        if (list.isEmpty()) return
        play(list, Random.nextInt(list.size), true)
    }

    fun playExternal(uri: Uri, title: String) = withCtl { c ->
        val m = MediaItem.Builder().setMediaId(uri.toString()).setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build()).build()
        c.setMediaItem(m); c.prepare(); c.play()
    }

    fun toggle() = withCtl { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() = withCtl { it.seekToNextMediaItem() }
    fun prev() = withCtl { it.seekToPrevious() }
    fun seek(ms: Long) = withCtl { it.seekTo(ms) }
    fun position(): Long = ctl?.currentPosition ?: 0L

    fun toggleShuffle() = withCtl { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withCtl {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun changeSpeed(s: Float) = withCtl { it.setPlaybackSpeed(s) }

    fun setSkipSilence(on: Boolean) {
        Prefs.skipSilence = on; Prefs.save()
        try { PlaybackService.exo?.skipSilenceEnabled = on } catch (_: Throwable) {}
    }

    // امسح كل القايمة ما عدا الأغنية الشغّالة
    fun clearQueue() = withCtl { c ->
        val i = c.currentMediaItemIndex
        if (c.mediaItemCount == 0 || i < 0) return@withCtl
        if (i < c.mediaItemCount - 1) c.removeMediaItems(i + 1, c.mediaItemCount)
        if (i > 0) c.removeMediaItems(0, i)
    }

    // حرّك أغنية في القايمة لفوق أو لتحت
    fun moveUp(i: Int) = move(i, i - 1)
    fun moveDown(i: Int) = move(i, i + 1)

    // ─── القائمة الحالية ───
    fun playNext(s: Song) = withCtl { c ->
        if (c.mediaItemCount == 0) { c.setMediaItem(item(s)); c.prepare(); c.play() }
        else c.addMediaItem(c.currentMediaItemIndex + 1, item(s))
    }

    fun enqueue(s: Song) = withCtl { c ->
        if (c.mediaItemCount == 0) { c.setMediaItem(item(s)); c.prepare(); c.play() }
        else c.addMediaItem(item(s))
    }

    fun enqueueAll(list: List<Song>) = withCtl { c ->
        if (list.isEmpty()) return@withCtl
        if (c.mediaItemCount == 0) { c.setMediaItems(list.map { item(it) }); c.prepare(); c.play() }
        else c.addMediaItems(list.map { item(it) })
    }

    fun removeAt(i: Int) = withCtl { c -> if (i in 0 until c.mediaItemCount) c.removeMediaItem(i) }

    fun jump(i: Int) = withCtl { c ->
        if (i in 0 until c.mediaItemCount) { c.seekToDefaultPosition(i); c.play() }
    }

    fun move(from: Int, to: Int) = withCtl { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    // ─── رجّع آخر جلسة (واقفة) لما التطبيق يفتح ───
    fun restore() {
        val c = ctl ?: return
        if (!Prefs.restoreQueue || c.mediaItemCount > 0 || Prefs.lastQueue.isBlank() || Media.byId.isEmpty()) return
        val ids = Prefs.lastQueue.split(',').mapNotNull { it.toLongOrNull() }
        val list = ids.mapNotNull { Media.byId[it] }
        if (list.isEmpty()) return
        val wanted = ids.getOrNull(Prefs.lastIndex)
        val idx = list.indexOfFirst { it.id == wanted }.takeIf { it >= 0 } ?: 0
        c.setMediaItems(list.map { item(it) }, idx, Prefs.lastPos)
        c.prepare()
    }

    // ─── مؤقت النوم ───
    fun sleep(minutes: Int) {
        sleepJob?.cancel()
        sleepAtTrackEnd = false
        if (minutes <= 0) { sleepEnd = 0L; return }
        val ms = minutes * 60_000L
        sleepEnd = System.currentTimeMillis() + ms
        sleepJob = Media.scope.launch {
            delay(ms)
            ctl?.pause()
            sleepEnd = 0L
        }
    }

    fun sleepAtTrackEnd() {
        sleepJob?.cancel()
        sleepEnd = 0L
        sleepAtTrackEnd = true
    }
}
