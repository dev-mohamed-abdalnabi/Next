package app.flowplay

import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// نافذة التشغيل السريع: بتظهر فوق أي تطبيق (مدير الملفات مثلًا) من غير ما تفتح التطبيق كله
class QuickPlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppInit.ensure(this)
        Music.connect()
        handle(intent)
        setContent { AppTheme { QuickUi(onOpenApp = { openApp() }, onClose = { finish() }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i?.action != Intent.ACTION_VIEW) return
        val u = i.data ?: return
        var name = (u.lastPathSegment ?: "").substringAfterLast('/').substringBeforeLast('.')
        if (u.scheme == "content") try {
            contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0).substringBeforeLast('.')
            }
        } catch (_: Throwable) {}
        Music.playExternal(u, name)
        i.action = null
    }

    // فتح التطبيق الكامل: الموسيقى بتكمّل شغّالة من غير انقطاع (الخدمة هي اللي بتشغّلها)
    private fun openApp() {
        try { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
        finish()
    }
}

@Composable
private fun QuickUi(onOpenApp: () -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val s = Music.current
    var pos by remember { mutableLongStateOf(0L) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) { while (true) { pos = Music.position(); delay(250) } }
    val dur = Music.duration
    val frac = if (dur > 0) pos.toFloat() / dur else 0f
    val none = remember { MutableInteractionSource() }

    Box(
        Modifier.fillMaxSize().clickable(interactionSource = none, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(cs.surfaceContainer)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MediaThumb(s?.uri, Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(cs.secondaryContainer), 200) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Ic.Music, null, Modifier.size(28.dp), tint = cs.onSecondaryContainer)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(s?.title ?: "جاري التحميل…", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!s?.artist.isNullOrBlank()) Text(s?.artist ?: "", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClose) { Icon(Ic.Close, "إغلاق") }
            }
            WavySeek(
                frac, Music.playing, onScrub = { scrub = it },
                onSeek = { f -> if (dur > 0) { val t = (f * dur).toLong(); pos = t; Music.seek(t) } }
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtMs(((scrub ?: frac) * dur).toLong()), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Text(fmtMs(dur), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton({ Music.prev() }, Modifier.size(56.dp)) { Icon(Ic.Prev, "السابق", Modifier.size(30.dp)) }
                Box(
                    Modifier.size(68.dp).clip(RoundedCornerShape(if (Music.playing) 24.dp else 34.dp)).background(cs.primary)
                        .clickable { Music.toggle() },
                    contentAlignment = Alignment.Center
                ) { Icon(if (Music.playing) Ic.Pause else Ic.Play, null, Modifier.size(32.dp), tint = cs.onPrimary) }
                IconButton({ Music.next() }, Modifier.size(56.dp)) { Icon(Ic.Next, "التالي", Modifier.size(30.dp)) }
            }
            FilledTonalButton(onOpenApp, Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Ic.Music, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("فتح التطبيق")
            }
        }
    }
}
