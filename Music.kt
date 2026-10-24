@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.flowplay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private class AlbumInfo(val id: Long, val name: String, val artist: String, val songs: List<Song>)

private val SongSortNames = listOf("الاسم", "الفنان", "الأحدث", "المدة")

private fun shareSong(c: Context, s: Song) = try {
    val i = Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, Uri.parse(s.uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    c.startActivity(Intent.createChooser(i, null))
} catch (_: Throwable) {}

private fun listFor(key: String): List<Song> = when {
    key == "fav" -> Media.songs.filter { it.id in Store.favs }
    key == "top" -> Media.songs.filter { (Store.plays[it.id] ?: 0) > 0 }.sortedByDescending { Store.plays[it.id] ?: 0 }.take(100)
    key == "new" -> Media.songs.sortedByDescending { it.added }.take(100)
    key == "recent" -> Store.recent.mapNotNull { Media.byId[it] }
    key.startsWith("pl:") -> Store.playlists.firstOrNull { it.id == key.drop(3) }?.ids?.mapNotNull { Media.byId[it] } ?: emptyList()
    key.startsWith("al:") -> Media.songs.filter { it.albumId.toString() == key.drop(3) }.sortedBy { it.title.lowercase() }
    key.startsWith("ar:") -> Media.songs.filter { it.artist == key.drop(3) }
    else -> emptyList()
}

private fun titleFor(key: String, l: List<Song>): String = when {
    key == "fav" -> "المفضلة"
    key == "top" -> "الأكثر تشغيلًا"
    key == "new" -> "أُضيفت مؤخرًا"
    key == "recent" -> "شُغّلت مؤخرًا"
    key.startsWith("pl:") -> Store.playlists.firstOrNull { it.id == key.drop(3) }?.name ?: "قائمة"
    key.startsWith("al:") -> l.firstOrNull()?.album ?: "ألبوم"
    key.startsWith("ar:") -> key.drop(3)
    else -> ""
}

// ═══════════════════════════ شاشة الموسيقى ═══════════════════════════

@Composable
fun MusicScreen(bottom: Dp) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var sub by rememberSaveable { mutableIntStateOf(0) }
    var q by rememberSaveable { mutableStateOf("") }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf<List<Long>?>(null) }
    var newPl by remember { mutableStateOf(false) }
    var renameDlg by remember { mutableStateOf(false) }
    var delDlg by remember { mutableStateOf(false) }
    var detailMenu by remember { mutableStateOf(false) }
    var infoSong by remember { mutableStateOf<Song?>(null) }
    var delSong by remember { mutableStateOf<Song?>(null) }

    val delLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) Media.refresh(ctx)
    }
    fun doDeleteSong(s: Song) {
        val ok = deleteMedia(ctx, listOf(Uri.parse(s.uri))) { delLauncher.launch(IntentSenderRequest.Builder(it).build()) }
        if (!ok) Toast.makeText(ctx, "تعذّر الحذف", Toast.LENGTH_SHORT).show()
    }

    BackHandler(enabled = detail != null) { detail = null }

    val all = Media.songs
    val sortKey = Prefs.songSort
    val songs = remember(all, q, sortKey) {
        val f = all.filter { q.isBlank() || it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }
        when (sortKey) {
            1 -> f.sortedBy { it.artist.lowercase() }
            2 -> f.sortedByDescending { it.added }
            3 -> f.sortedByDescending { it.dur }
            else -> f
        }
    }
    val albums = remember(all, q) {
        all.groupBy { it.albumId }.map { (id, l) -> AlbumInfo(id, l[0].album, l[0].artist, l) }
            .filter { q.isBlank() || it.name.contains(q, true) || it.artist.contains(q, true) }
            .sortedBy { it.name.lowercase() }
    }
    val artists = remember(all, q) {
        all.groupBy { it.artist }.toList().filter { q.isBlank() || it.first.contains(q, true) }.sortedBy { it.first.lowercase() }
    }
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    val playingId = Music.current?.id ?: -1L

    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(cs.tertiaryContainer.copy(alpha = 0.8f), Color.Transparent),
                    center = Offset(size.width * (if (rtl) 0.12f else 0.88f), size.height * 0.04f), radius = size.width * 0.95f
                )
            )
        }
    ) {
        val d = detail
        if (d != null) {
            val list = listFor(d)
            val title = titleFor(d, list)
            val isPl = d.startsWith("pl:")
            LazyVerticalGrid(
                GridCells.Fixed(1), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom)
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton({ detail = null }) { Icon(Ic.Back, "رجوع") }
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${list.size} أغنية", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                        if (isPl) Box {
                            IconButton({ detailMenu = true }) { Icon(Ic.More, "المزيد") }
                            DropdownMenu(detailMenu, { detailMenu = false }) {
                                DropdownMenuItem({ Text("تغيير الاسم") }, { detailMenu = false; renameDlg = true })
                                DropdownMenuItem({ Text("حذف القائمة", color = cs.error) }, { detailMenu = false; delDlg = true })
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton({ Music.play(list, 0) }, enabled = list.isNotEmpty()) {
                            Icon(Ic.Play, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("تشغيل")
                        }
                        OutlinedButton({ Music.shuffleAll(list) }, enabled = list.isNotEmpty()) {
                            Icon(Ic.Shuffle, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("عشوائي")
                        }
                        OutlinedButton({ Music.enqueueAll(list); Toast.makeText(ctx, "اتضافت للقائمة الحالية", Toast.LENGTH_SHORT).show() }, enabled = list.isNotEmpty()) {
                            Icon(Ic.Queue, null, Modifier.size(20.dp))
                        }
                    }
                }
                if (list.isEmpty()) item {
                    EmptyState(
                        Ic.Music, if (d == "fav") "مفيش مفضلة لسه" else "القائمة فاضية",
                        if (d == "fav") "اضغط على القلب في أي أغنية." else "الأغاني هتظهر هنا."
                    )
                }
                items(list, key = { it.id }) { s ->
                    SongRow(
                        s, s.id == playingId,
                        onClick = { Music.play(list, list.indexOf(s)) },
                        onAddPl = { addTo = listOf(s.id) },
                        onRemove = if (isPl) ({ Store.removeFromPlaylist(d.drop(3), s.id) }) else null,
                        onInfo = { infoSong = s }, onDelete = { delSong = s }
                    )
                }
            }
            if (renameDlg) NameDialog("تغيير اسم القائمة", title, { renameDlg = false }) { Store.renamePlaylist(d.drop(3), it); renameDlg = false }
            if (delDlg) AlertDialog(
                onDismissRequest = { delDlg = false },
                title = { Text("حذف القائمة؟") }, text = { Text("\"$title\" هتتمسح (الأغاني نفسها هتفضل على جهازك).") },
                confirmButton = { TextButton({ Store.deletePlaylist(d.drop(3)); delDlg = false; detail = null }) { Text("حذف", color = cs.error) } },
                dismissButton = { TextButton({ delDlg = false }) { Text("إلغاء") } }
            )
        } else {
            LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.fillMaxSize().imePadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(span = full) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp, bottom = 0.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("الموسيقى", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                if (all.isNotEmpty()) "Next · ${all.size} أغنية" else "Next",
                                style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (sub == 0) Box {
                                IconButton({ sortMenu = true }) { Icon(Icons.Rounded.Menu, "القائمة") }
                                DropdownMenu(sortMenu, { sortMenu = false }) {
                                    if (songs.isNotEmpty()) {
                                        DropdownMenuItem(text = { Text("تشغيل الكل") }, leadingIcon = { Icon(Ic.Play, null) },
                                            onClick = { sortMenu = false; Music.play(songs, 0) })
                                        DropdownMenuItem(text = { Text("عشوائي") }, leadingIcon = { Icon(Ic.Shuffle, null) },
                                            onClick = { sortMenu = false; Music.shuffleAll(songs) })
                                        HorizontalDivider()
                                    }
                                    SongSortNames.forEachIndexed { i, n ->
                                        DropdownMenuItem(
                                            text = { Text("ترتيب: $n") },
                                            leadingIcon = { if (sortKey.coerceIn(0, 3) == i) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp)) },
                                            onClick = { Prefs.songSort = i; Prefs.save(); sortMenu = false }
                                        )
                                    }
                                }
                            }
                            CookieIcon(Ic.Music, 44.dp, cs.tertiary, cs.onTertiary)
                        }
                    }
                }
                if (sub != 3) item(span = full) {
                    TextField(
                        value = q, onValueChange = { q = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(26.dp), colors = fieldColors(),
                        placeholder = { Text("ابحث عن أغنية أو فنان أو ألبوم", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Rounded.Close, "مسح") } }
                    )
                }
                item(span = full) { Switcher(listOf("الأغاني", "الألبومات", "الفنانين", "القوائم"), sub) { sub = it } }

                if (!Media.loaded) item(span = full) {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (all.isEmpty() && sub != 3) item(span = full) {
                    EmptyState(Ic.Music, "مفيش أغاني", "لما يبقى فيه موسيقى على جهازك هتظهر هنا.")
                } else when (sub) {
                    0 -> {
                        if (songs.isEmpty()) item(span = full) { EmptyState(Icons.Rounded.Search, "مفيش نتائج", "جرّب كلمة تانية.") }
                        items(songs, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { s ->
                            SongRow(
                                s, s.id == playingId, onClick = { Music.play(songs, songs.indexOf(s)) }, onAddPl = { addTo = listOf(s.id) },
                                onInfo = { infoSong = s }, onDelete = { delSong = s }
                            )
                        }
                    }
                    1 -> {
                        if (albums.isEmpty()) item(span = full) { EmptyState(Icons.Rounded.Search, "مفيش نتائج", "جرّب كلمة تانية.") }
                        items(albums, key = { it.id }) { al ->
                            Column(Modifier.clip(RoundedCornerShape(28.dp)).clickable { detail = "al:" + al.id }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                MediaThumb(al.songs[0].uri, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(cs.secondaryContainer), 400) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CookieIcon(Ic.Album, 72.dp, cs.tertiaryContainer, cs.onTertiaryContainer, spin = false)
                                    }
                                }
                                Text(al.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp))
                                Text("${al.artist} • ${al.songs.size}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                            }
                        }
                    }
                    2 -> {
                        if (artists.isEmpty()) item(span = full) { EmptyState(Icons.Rounded.Search, "مفيش نتائج", "جرّب كلمة تانية.") }
                        items(artists, key = { it.first }, span = { GridItemSpan(maxLineSpan) }) { (name, l) ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable { detail = "ar:$name" }.padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CookieIcon(Ic.Artist, 56.dp, cs.secondaryContainer, cs.onSecondaryContainer, spin = false)
                                Column(Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${l.size} أغنية", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    else -> {
                        val smart = listOf(
                            Triple("fav", "المفضلة", Ic.HeartFill), Triple("top", "الأكثر تشغيلًا", Ic.Play),
                            Triple("new", "أُضيفت مؤخرًا", Ic.Plus), Triple("recent", "شُغّلت مؤخرًا", Ic.Refresh)
                        )
                        items(smart, key = { it.first }, span = { GridItemSpan(maxLineSpan) }) { (k, n, ic) ->
                            PlaylistRow(ic, n, "${listFor(k).size} أغنية", cs.tertiaryContainer, cs.onTertiaryContainer) { detail = k }
                        }
                        if (Store.playlists.isNotEmpty()) item(span = full) { SectionTitle("قوائمي", Modifier.padding(top = 10.dp)) }
                        items(Store.playlists.toList(), key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { p ->
                            PlaylistRow(Ic.Queue, p.name, "${p.ids.size} أغنية", cs.secondaryContainer, cs.onSecondaryContainer) { detail = "pl:" + p.id }
                        }
                        item(span = full) {
                            Spacer(Modifier.height(6.dp))
                            BigButton("قائمة تشغيل جديدة", icon = Ic.Plus, tonal = true) { newPl = true }
                        }
                    }
                }
            }
        }
    }

    addTo?.let { ids -> AddToPlaylistDialog(ids) { addTo = null } }
    if (newPl) NameDialog("قائمة تشغيل جديدة", "", { newPl = false }) { Store.createPlaylist(it); newPl = false }

    infoSong?.let { s ->
        AlertDialog(
            onDismissRequest = { infoSong = null }, title = { Text("معلومات الأغنية") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "العنوان" to s.title, "الفنان" to s.artist, "الألبوم" to s.album, "المدة" to fmtMs(s.dur),
                        "المجلد" to s.folder, "عدد مرات التشغيل" to (Store.plays[s.id] ?: 0).toString()
                    ).filter { it.second.isNotBlank() }.forEach { (k, v) ->
                        Column {
                            Text(k, style = MaterialTheme.typography.labelMedium, color = cs.primary)
                            Text(v, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ infoSong = null }) { Text("تم") } }
        )
    }
    delSong?.let { s ->
        AlertDialog(
            onDismissRequest = { delSong = null }, title = { Text("حذف الأغنية؟") },
            text = { Text("\"${s.title}\"\nهتتمسح من جهازك نهائيًا.", maxLines = 4, overflow = TextOverflow.Ellipsis) },
            confirmButton = { TextButton({ val t = s; delSong = null; doDeleteSong(t) }) { Text("حذف", color = cs.error) } },
            dismissButton = { TextButton({ delSong = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun PlaylistRow(icon: ImageVector, title: String, sub: String, bg: Color, fg: Color, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(26.dp), tint = fg)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun SongRow(
    s: Song, playing: Boolean, onClick: () -> Unit, onAddPl: () -> Unit, onRemove: (() -> Unit)? = null,
    onInfo: () -> Unit = {}, onDelete: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(if (playing) cs.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        MediaThumb(s.uri, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(cs.secondaryContainer), 160) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Ic.Music, null, Modifier.size(24.dp), tint = cs.onSecondaryContainer)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (playing) cs.primary else cs.onSurface)
            Text(s.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurfaceVariant)
        }
        if (s.dur > 0) Text(fmtMs(s.dur), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Box {
            IconButton({ menu = true }, Modifier.size(36.dp)) { Icon(Ic.More, "المزيد", Modifier.size(20.dp)) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("تشغيل بعد الحالية") }, { menu = false; Music.playNext(s); Toast.makeText(ctx, "هتشتغل بعد الحالية", Toast.LENGTH_SHORT).show() })
                DropdownMenuItem({ Text("إضافة للقائمة الحالية") }, { menu = false; Music.enqueue(s); Toast.makeText(ctx, "اتضافت للقائمة", Toast.LENGTH_SHORT).show() })
                DropdownMenuItem({ Text("إضافة إلى قائمة تشغيل") }, { menu = false; onAddPl() })
                DropdownMenuItem({ Text(if (Store.isFav(s.id)) "إزالة من المفضلة" else "إضافة للمفضلة") }, { menu = false; Store.toggleFav(s.id) })
                DropdownMenuItem({ Text("مشاركة") }, { menu = false; shareSong(ctx, s) })
                DropdownMenuItem({ Text("معلومات الأغنية") }, { menu = false; onInfo() })
                if (onDelete != null) DropdownMenuItem({ Text("حذف من الجهاز", color = cs.error) }, { menu = false; onDelete() })
                if (onRemove != null) DropdownMenuItem({ Text("إزالة من القائمة", color = cs.error) }, { menu = false; onRemove() })
            }
        }
    }
}

// ═══════════════════════════ حوارات ═══════════════════════════

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(t, { t = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("اسم القائمة") }) },
        confirmButton = { TextButton({ onOk(t) }, enabled = t.isNotBlank()) { Text("حفظ") } },
        dismissButton = { TextButton(onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddToPlaylistDialog(ids: List<Long>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog("قائمة تشغيل جديدة", "", { creating = false }) {
            Store.createPlaylist(it, ids); Toast.makeText(ctx, "اتعملت القائمة", Toast.LENGTH_SHORT).show(); onDismiss()
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("إضافة إلى قائمة") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { creating = true }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Ic.Plus, null, tint = cs.primary)
                    Text("قائمة جديدة", color = cs.primary, style = MaterialTheme.typography.titleSmall)
                }
                Store.playlists.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .clickable { Store.addToPlaylist(p.id, ids); Toast.makeText(ctx, "اتضافت إلى ${p.name}", Toast.LENGTH_SHORT).show(); onDismiss() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Ic.Queue, null)
                        Text(p.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Text("${p.ids.size}", color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("إلغاء") } }
    )
}

// ═══════════════════════════ المشغّل المصغّر ═══════════════════════════

@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    MiniPlayer(onOpen = onOpen, onDismiss = { Music.dismiss() })
}

@Composable
fun MiniPlayer(onOpen: () -> Unit, onDismiss: () -> Unit) {
    val s = Music.current ?: return
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var pos by remember { mutableLongStateOf(0L) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val threshold = remember(density) { with(density) { 50.dp.toPx() } }

    LaunchedEffect(s) { offsetY = 0f }
    LaunchedEffect(Music.playing, s) {
        while (true) { pos = Music.position(); delay(if (Music.playing) 500 else 1500) }
    }
    val frac = if (Music.duration > 0) (pos.toFloat() / Music.duration).coerceIn(0f, 1f) else 0f
    val alphaVal = (1f - (offsetY / (threshold * 2.2f))).coerceIn(0f, 1f)

    Surface(
        onClick = onOpen, shape = RoundedCornerShape(30.dp), color = cs.primaryContainer,
        shadowElevation = (8.dp * alphaVal),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .graphicsLayer { alpha = alphaVal }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    offsetY = (offsetY + delta).coerceAtLeast(0f)
                },
                onDragStopped = { velocity ->
                    if (offsetY > threshold || (velocity > 600f && offsetY > 10f)) {
                        scope.launch {
                            val endY = with(density) { 120.dp.toPx() }
                            Animatable(offsetY).animateTo(endY, tween(120)) {
                                offsetY = value
                            }
                            onDismiss()
                        }
                    } else {
                        scope.launch {
                            Animatable(offsetY).animateTo(0f, spring(0.75f, 600f)) {
                                offsetY = value
                            }
                        }
                    }
                }
            )
    ) {
        Column {
            Row(
                Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MediaThumb(s.uri, Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(cs.secondaryContainer), 160) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Ic.Music, null, Modifier.size(22.dp), tint = cs.onSecondaryContainer)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(s.title, style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.artist, style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton({ Music.prev() }, Modifier.size(40.dp)) { Icon(Ic.Prev, "السابق", Modifier.size(22.dp), tint = cs.onPrimaryContainer) }
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(cs.primary).clickable { Music.toggle() },
                    contentAlignment = Alignment.Center
                ) { Icon(if (Music.playing) Ic.Pause else Ic.Play, null, Modifier.size(22.dp), tint = cs.onPrimary) }
                IconButton({ Music.next() }, Modifier.size(40.dp)) { Icon(Ic.Next, "التالي", Modifier.size(22.dp), tint = cs.onPrimaryContainer) }
            }
            Box(Modifier.padding(horizontal = 22.dp).padding(bottom = 8.dp).fillMaxWidth().height(3.dp).clip(CircleShape).background(cs.onPrimaryContainer.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(cs.primary))
            }
        }
    }
}
            
// ═══════════════════════════ شاشة التشغيل الكاملة ═══════════════════════════

@Composable
private fun PlayFab(playing: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pressed) 20.dp else if (playing) 30.dp else 40.dp, spring(0.55f, 600f), label = "pf")
    Box(
        Modifier.size(80.dp).clip(RoundedCornerShape(r)).background(cs.primary)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(if (playing) Ic.Pause else Ic.Play, null, Modifier.size(36.dp), tint = cs.onPrimary) }
}

@Composable
private fun PillBtn(icon: ImageVector, text: String?, active: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.height(44.dp).clip(CircleShape).background(if (active) cs.primaryContainer else cs.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (active) cs.onPrimaryContainer else cs.onSurface)
        if (text != null) Text(text, style = MaterialTheme.typography.labelLarge, color = if (active) cs.onPrimaryContainer else cs.onSurface, maxLines = 1)
    }
}

@Composable
fun NowPlaying(onClose: () -> Unit) {
    BackHandler { onClose() }
    val cs = MaterialTheme.colorScheme
    val s = Music.current
    var showQueue by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var saveQ by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf<List<Long>?>(null) }
    var pos by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var scrub by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(Music.playing, Music.current) {
        while (true) { pos = Music.position(); now = System.currentTimeMillis(); delay(if (Music.playing) 200 else 800) }
    }
    val dur = Music.duration
    val frac = if (dur > 0) pos.toFloat() / dur else 0f
    val artScale by animateFloatAsState(if (Music.playing) 1f else 0.9f, spring(0.6f, 300f), label = "art")

    Surface(Modifier.fillMaxSize(), color = cs.surface) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(cs.primaryContainer, cs.surface, cs.surface)))) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClose) { Icon(Ic.Down, "إغلاق") }
                    Text("يتم التشغيل الآن", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
                    IconButton({ showQueue = true }) { Icon(Ic.Queue, "القائمة") }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = artScale; scaleY = artScale }
                        .clip(RoundedCornerShape(44.dp)).background(cs.secondaryContainer)
                ) {
                    MediaThumb(s?.uri, Modifier.fillMaxSize(), 900) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CookieIcon(Ic.Music, 200.dp, cs.primary, cs.onPrimary, spin = Music.playing)
                        }
                    }
                }
                Spacer(Modifier.height(26.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s?.title ?: "", style = MaterialTheme.typography.headlineSmall, maxLines = 1, modifier = Modifier.basicMarquee())
                        Text(s?.artist ?: "", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (s != null && s.id > 0) {
                        val fav = Store.isFav(s.id)
                        IconButton({ Store.toggleFav(s.id) }) {
                            Icon(if (fav) Ic.HeartFill else Ic.Heart, "مفضلة", Modifier.size(28.dp), tint = if (fav) cs.primary else cs.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                WavySeek(
                    frac, Music.playing, onScrub = { scrub = it },
                    onSeek = { f -> if (dur > 0) { val t = (f * dur).toLong(); pos = t; Music.seek(t) } }
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmtMs(((scrub ?: frac) * dur).toLong()), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Text(fmtMs(dur), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ Music.toggleShuffle() }, Modifier.size(52.dp)) {
                        Icon(Ic.Shuffle, "عشوائي", Modifier.size(26.dp), tint = if (Music.shuffle) cs.primary else cs.onSurfaceVariant)
                    }
                    IconButton({ Music.prev() }, Modifier.size(60.dp)) { Icon(Ic.Prev, "السابق", Modifier.size(34.dp)) }
                    PlayFab(Music.playing) { Music.toggle() }
                    IconButton({ Music.next() }, Modifier.size(60.dp)) { Icon(Ic.Next, "التالي", Modifier.size(34.dp)) }
                    IconButton({ Music.cycleRepeat() }, Modifier.size(52.dp)) {
                        Icon(
                            if (Music.repeat == Player.REPEAT_MODE_ONE) Ic.RepeatOne else Ic.Repeat, "تكرار", Modifier.size(26.dp),
                            tint = if (Music.repeat != Player.REPEAT_MODE_OFF) cs.primary else cs.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                val sleepLeft = if (Music.sleepEnd > now) ((Music.sleepEnd - now) / 60000 + 1).toInt() else 0
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillBtn(Ic.Speed, if (Music.speed == 1f) "السرعة" else String.format(Locale.US, "%.2fx", Music.speed).replace(".00x", "x"), Music.speed != 1f) { showSpeed = true }
                    PillBtn(
                        Ic.Timer, if (sleepLeft > 0) "$sleepLeft د" else if (Music.sleepAtTrackEnd) "آخر أغنية" else "المؤقت",
                        sleepLeft > 0 || Music.sleepAtTrackEnd
                    ) { showSleep = true }
                    PillBtn(Ic.Eq, "المعادل", Prefs.eqOn) { showEq = true }
                    PillBtn(Ic.Forward, "تخطي الصمت", Prefs.skipSilence) { Music.setSkipSilence(!Prefs.skipSilence) }
                    if (s != null && s.id > 0) PillBtn(Ic.Plus, "قائمة") { addTo = listOf(s.id) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showQueue) {
        val st = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet({ showQueue = false }, sheetState = st) {
            val ls = rememberLazyListState(initialFirstVisibleItemIndex = max(0, Music.index - 2))
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("قائمة التشغيل (${Music.queue.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton({ saveQ = true }, enabled = Music.queue.any { it.id > 0 }) { Text("حفظ كقائمة") }
                TextButton({ Music.clearQueue() }, enabled = Music.queue.size > 1) { Text("مسح الباقي", color = cs.error) }
            }
            LazyColumn(Modifier.heightIn(max = 560.dp), state = ls, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                itemsIndexed(Music.queue) { i, q ->
                    val cur = i == Music.index
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(if (cur) cs.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
                            .clickable { Music.jump(i) }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MediaThumb(q.uri, Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(cs.secondaryContainer), 160) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Ic.Music, null, Modifier.size(20.dp), tint = cs.onSecondaryContainer) }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(q.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = if (cur) cs.primary else cs.onSurface)
                            Text(q.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        if (i > 0) IconButton({ Music.moveUp(i) }, Modifier.size(32.dp)) {
                            Icon(Ic.Down, "لفوق", Modifier.size(18.dp).graphicsLayer { rotationZ = 180f })
                        }
                        if (i < Music.queue.size - 1) IconButton({ Music.moveDown(i) }, Modifier.size(32.dp)) { Icon(Ic.Down, "لتحت", Modifier.size(18.dp)) }
                        if (!cur) IconButton({ Music.removeAt(i) }, Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, "إزالة", Modifier.size(18.dp)) }
                    }
                }
            }
        }
    }

    if (showEq) {
        val st = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet({ showEq = false }, sheetState = st) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("المعادل الصوتي", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    Toggle(Prefs.eqOn) { Fx.setOn(it) }
                }
                if (!Fx.available) {
                    Text("شغّل أي أغنية الأول عشان المعادل يشتغل.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                } else {
                    if (Fx.presets.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Fx.presets.forEachIndexed { i, n -> AssistChip(onClick = { Fx.usePreset(i) }, label = { Text(n) }, enabled = Prefs.eqOn) }
                    }
                    Fx.freqs.forEachIndexed { i, hz ->
                        val lv = Fx.levels.getOrNull(i) ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (hz >= 1000) String.format(Locale.US, "%.1fk", hz / 1000f) else "$hz", Modifier.width(52.dp), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                            Slider(
                                lv.toFloat(), { Fx.setBand(i, it.roundToInt()) }, Modifier.weight(1f),
                                enabled = Prefs.eqOn, valueRange = Fx.minLevel.toFloat()..Fx.maxLevel.toFloat()
                            )
                            Text(String.format(Locale.US, "%+.0f", lv / 100f), Modifier.width(36.dp), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
                        }
                    }
                    TextButton({ Fx.reset() }, enabled = Prefs.eqOn) { Text("تصفير") }
                    if (Fx.bassOk) {
                        Text("تعزيز الباس", style = MaterialTheme.typography.titleSmall)
                        Slider(Prefs.bassStr.toFloat(), { Fx.setBass(it.roundToInt()) }, enabled = Prefs.eqOn, valueRange = 0f..1000f)
                    }
                    if (Fx.virtOk) {
                        Text("الصوت المحيطي", style = MaterialTheme.typography.titleSmall)
                        Slider(Prefs.virtStr.toFloat(), { Fx.setVirt(it.roundToInt()) }, enabled = Prefs.eqOn, valueRange = 0f..1000f)
                    }
                }
            }
        }
    }

    if (showSleep) AlertDialog(
        onDismissRequest = { showSleep = false }, title = { Text("مؤقت النوم") },
        text = {
            Column {
                val active = Music.sleepEnd > now || Music.sleepAtTrackEnd
                if (active) SleepOpt("إيقاف المؤقت", true) { Music.sleep(0); showSleep = false }
                listOf(15, 30, 45, 60, 90).forEach { m -> SleepOpt("$m دقيقة", false) { Music.sleep(m); showSleep = false } }
                SleepOpt("لما الأغنية الحالية تخلص", false) { Music.sleepAtTrackEnd(); showSleep = false }
            }
        },
        confirmButton = {}, dismissButton = { TextButton({ showSleep = false }) { Text("إغلاق") } }
    )

    if (showSpeed) AlertDialog(
        onDismissRequest = { showSpeed = false }, title = { Text("سرعة التشغيل") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(String.format(Locale.US, "%.2fx", Music.speed), Modifier.fillMaxWidth(), style = MaterialTheme.typography.displaySmall, color = cs.primary, textAlign = TextAlign.Center)
                Slider(Music.speed, { Music.changeSpeed(((it * 20).roundToInt() / 20f)) }, valueRange = 0.5f..2f)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.75f, 1f, 1.25f, 1.5f).forEach { v ->
                        FilterChip(Music.speed == v, { Music.changeSpeed(v) }, { Text(if (v == 1f) "1x" else "${v}x", maxLines = 1) }, Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton({ showSpeed = false }) { Text("تم") } }
    )

    if (saveQ) NameDialog("حفظ القائمة الحالية", "", { saveQ = false }) {
        Store.createPlaylist(it, Music.queue.map { q -> q.id }.filter { id -> id > 0 }); saveQ = false
    }

    addTo?.let { ids -> AddToPlaylistDialog(ids) { addTo = null } }
}

@Composable
private fun SleepOpt(text: String, danger: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Text(
        text, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        style = MaterialTheme.typography.bodyLarge, color = if (danger) cs.error else cs.onSurface
    )
}
