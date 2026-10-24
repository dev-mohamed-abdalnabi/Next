@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.flowplay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// بيفتح مشغّل الفيديو بقائمة (الفيديوهات اللي ظاهرة) وبيبدأ من الفيديو المختار
fun openVideo(c: Context, list: List<Vid>, index: Int) {
    VQ.items = list
    VQ.start = index.coerceIn(0, (list.size - 1).coerceAtLeast(0))
    try { c.startActivity(Intent(c, VideoActivity::class.java).putExtra("q", true)) } catch (_: Throwable) {}
}

private fun shareVids(c: Context, l: List<Vid>) = try {
    val s = if (l.size == 1) Intent(Intent.ACTION_SEND).setType("video/*").putExtra(Intent.EXTRA_STREAM, Uri.parse(l[0].uri))
    else Intent(Intent.ACTION_SEND_MULTIPLE).setType("video/*")
        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(l.map { Uri.parse(it.uri) }))
    c.startActivity(Intent.createChooser(s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
} catch (_: Throwable) {}

private val SortNames = listOf("الأحدث", "الاسم", "الأطول", "الأكبر حجمًا")

@Composable
fun VideoScreen(bottom: Dp) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    var q by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var sel by remember { mutableStateOf(setOf<String>()) }
    var sortMenu by remember { mutableStateOf(false) }
    var urlDlg by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var delTarget by remember { mutableStateOf<List<Vid>?>(null) }
    val selecting = sel.isNotEmpty()

    BackHandler(enabled = selecting) { sel = emptySet() }
    BackHandler(enabled = folder != null && !selecting) { folder = null }

    val delLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) { sel = emptySet(); Media.refresh(ctx) }
    }
    fun doDelete(list: List<Vid>) {
        val ok = deleteMedia(ctx, list.map { Uri.parse(it.uri) }) { delLauncher.launch(IntentSenderRequest.Builder(it).build()) }
        if (!ok) scope.launch { snack.showSnackbar("تعذّر الحذف") }
        else if (Build.VERSION.SDK_INT < 30) sel = emptySet()
    }

    val all = Media.videos
    val sortKey = Prefs.vidSort
    val vfavs = Store.vfavs
    val list = remember(all, q, folder, sortKey, mode, vfavs) {
        val f = all.filter {
            (folder == null || it.folder == folder) && (q.isBlank() || it.title.contains(q, true)) &&
                (mode != 2 || folder != null || it.uri in vfavs)
        }
        when (sortKey) {
            1 -> f.sortedBy { it.title.lowercase() }
            2 -> f.sortedByDescending { it.dur }
            3 -> f.sortedByDescending { it.size }
            else -> f
        }
    }
    val folders = remember(all, q) {
        all.filter { q.isBlank() || it.title.contains(q, true) }
            .groupBy { it.folder }.toList().sortedBy { it.first.lowercase() }
    }
    val cont = all.filter { Store.vpos.containsKey(it.uri) }
        .sortedByDescending { Store.vlast[it.uri] ?: 0L }.take(10)

    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    // توهّج لوني ناعم ثابت في أعلى الشاشة
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(cs.primaryContainer.copy(alpha = 0.85f), Color.Transparent),
                    center = Offset(size.width * (if (rtl) 0.12f else 0.88f), size.height * 0.04f), radius = size.width * 0.95f
                )
            )
        }
    ) {
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(span = full) {
                if (folder == null) Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(top = 24.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Next", style = MaterialTheme.typography.titleMedium, color = cs.primary)
                        Text("الفيديو", style = MaterialTheme.typography.displaySmall)
                        if (all.isNotEmpty()) Text("${all.size} فيديو", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
                    }
                    CookieIcon(Ic.Video, 72.dp, cs.primary, cs.onPrimary)
                } else Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton({ folder = null }) { Icon(Ic.Back, "رجوع") }
                    Column {
                        Text(folder ?: "", style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${list.size} فيديو", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
            if (selecting) item(span = full) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.secondaryContainer)
                        .padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${sel.size} محدد", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = cs.onSecondaryContainer)
                    TextButton({ sel = list.map { it.uri }.toSet() }) { Text("الكل") }
                    IconButton({ shareVids(ctx, all.filter { it.uri in sel }) }) { Icon(Icons.Rounded.Share, "مشاركة") }
                    IconButton({ delTarget = all.filter { it.uri in sel } }) { Icon(Icons.Rounded.Delete, "حذف") }
                    IconButton({ sel = emptySet() }) { Icon(Icons.Rounded.Close, "إلغاء التحديد") }
                }
            }
            item(span = full) {
                TextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    shape = RoundedCornerShape(30.dp), colors = fieldColors(),
                    placeholder = { Text("ابحث في الفيديوهات") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Rounded.Close, "مسح") } }
                )
            }
            item(span = full) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (folder == null) listOf("الكل", "المجلدات", "المفضلة").forEachIndexed { i, l ->
                        FilterChip(
                            selected = mode == i, onClick = { mode = i }, label = { Text(l) },
                            leadingIcon = if (mode == i) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box {
                        AssistChip(
                            onClick = { sortMenu = true }, label = { Text(SortNames[sortKey.coerceIn(0, 3)]) },
                            leadingIcon = { Icon(Ic.Sort, null, Modifier.size(18.dp)) }
                        )
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            SortNames.forEachIndexed { i, n ->
                                DropdownMenuItem(text = { Text(n) }, onClick = { Prefs.vidSort = i; Prefs.save(); sortMenu = false })
                            }
                        }
                    }
                }
            }
            if (!selecting && (mode != 1 || folder != null)) item(span = full) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (list.isNotEmpty()) FilledTonalButton({ openVideo(ctx, list, 0) }) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("تشغيل الكل")
                    }
                    if (list.isNotEmpty()) OutlinedButton({ val sh = list.shuffled(); openVideo(ctx, sh, 0) }) { Text("عشوائي") }
                    OutlinedButton({ urlDlg = true }) {
                        Icon(Ic.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("رابط")
                    }
                }
            }

            if (cont.isNotEmpty() && mode == 0 && folder == null && q.isBlank() && !selecting) {
                item(span = full) {
                    Column {
                        Text("أكمل المشاهدة", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(cont, key = { it.uri }) { v -> ContinueCard(v) { openVideo(ctx, cont, cont.indexOf(v)) } }
                        }
                    }
                }
            }

            if (!Media.loaded) item(span = full) {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (all.isEmpty()) item(span = full) {
                EmptyState(Ic.Video, "مفيش فيديوهات", "لما يبقى فيه فيديوهات على جهازك هتظهر هنا.")
            } else if (mode == 1 && folder == null) {
                if (folders.isEmpty()) item(span = full) { EmptyState(Icons.Rounded.Search, "مفيش نتائج", "جرّب كلمة تانية.") }
                items(folders, key = { it.first }) { (name, vids) ->
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1.35f).clip(RoundedCornerShape(28.dp))
                            .background(cs.surfaceContainerHigh).clickable { folder = name; q = "" }
                    ) {
                        MediaThumb(vids.first().uri, Modifier.fillMaxSize(), 400) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CookieIcon(Ic.Folder, 72.dp, cs.primaryContainer, cs.onPrimaryContainer, spin = false)
                            }
                        }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
                        Box(
                            Modifier.align(Alignment.TopStart).padding(10.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)).padding(7.dp)
                        ) { Icon(Ic.Folder, null, Modifier.size(16.dp), tint = Color.White) }
                        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                            Text(name, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${vids.size} فيديو", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            } else {
                if (list.isEmpty()) item(span = full) {
                    if (mode == 2 && folder == null && q.isBlank()) EmptyState(Ic.Heart, "مفيش مفضلة لسه", "اضغط على القلب في أي فيديو عشان يظهر هنا.")
                    else EmptyState(Icons.Rounded.Search, "مفيش نتائج", "جرّب كلمة تانية.")
                }
                items(list, key = { it.uri }) { v ->
                    VideoCard(
                        v, selected = v.uri in sel,
                        onClick = {
                            if (selecting) sel = if (v.uri in sel) sel - v.uri else sel + v.uri
                            else openVideo(ctx, list, list.indexOf(v))
                        },
                        onLong = { sel = if (v.uri in sel) sel - v.uri else sel + v.uri },
                        fav = v.uri in vfavs, onFav = { Store.toggleVFav(v.uri) },
                        onShare = { shareVids(ctx, listOf(v)) },
                        onDelete = { delTarget = listOf(v) }
                    )
                }
            }
        }
    }

    if (urlDlg) AlertDialog(
        onDismissRequest = { urlDlg = false },
        title = { Text("تشغيل من رابط") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    url, { url = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("https://…/video.mp4 أو m3u8") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = {
                        val u = url.trim()
                        if (u.isNotEmpty()) { urlDlg = false; openVideo(ctx, listOf(streamVid(u)), 0) }
                    })
                )
                TextButton({ clip.getText()?.text?.let { url = it.trim() } }) { Text("لصق من الحافظة") }
                Footnote("بيشغّل روابط الفيديو المباشرة (MP4 / MKV / HLS / DASH).")
            }
        },
        confirmButton = {
            TextButton(
                { val u = url.trim(); if (u.isNotEmpty()) { urlDlg = false; openVideo(ctx, listOf(streamVid(u)), 0) } },
                enabled = url.isNotBlank()
            ) { Text("تشغيل") }
        },
        dismissButton = { TextButton({ urlDlg = false }) { Text("إلغاء") } }
    )

    delTarget?.let { d ->
        AlertDialog(
            onDismissRequest = { delTarget = null },
            title = { Text(if (d.size == 1) "حذف الفيديو؟" else "حذف ${d.size} فيديو؟") },
            text = { Text(if (d.size == 1) "\"${d[0].title}\"\nهيتمسح من جهازك نهائيًا." else "هيتمسحوا من جهازك نهائيًا.", maxLines = 4, overflow = TextOverflow.Ellipsis) },
            confirmButton = { TextButton({ val t = d; delTarget = null; doDelete(t) }) { Text("حذف", color = cs.error) } },
            dismissButton = { TextButton({ delTarget = null }) { Text("إلغاء") } }
        )
    }
}

fun streamVid(u: String): Vid {
    val name = u.substringBefore('?').substringAfterLast('/').substringBeforeLast('.').ifBlank { u }
    return Vid(-1, u, name, 0, 0, 0, 0, 0, "")
}

@Composable
private fun ProgressLine(frac: Float, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.4f))) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(cs.primary))
    }
}

@Composable
private fun ContinueCard(v: Vid, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pos = Store.videoPos(v.uri)
    Column(Modifier.width(230.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.7f).clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            MediaThumb(v.uri, Modifier.fillMaxSize(), 400) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CookieIcon(Ic.Video, 56.dp, cs.primaryContainer, cs.onPrimaryContainer, spin = false)
                }
            }
            Box(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(12.dp)) {
                Icon(Ic.Play, null, Modifier.size(22.dp), tint = Color.White)
            }
            if (v.dur > 0) ProgressLine(pos.toFloat() / v.dur, Modifier.align(Alignment.BottomCenter))
        }
        Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp))
        if (v.dur > 0) Text(
            "باقي ${fmtMs(v.dur - pos)}", style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun VideoCard(
    v: Vid, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit,
    fav: Boolean, onFav: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val pos = Store.vpos[v.uri] ?: 0L
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.35f).clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHigh)
                .combinedClickable(onClick = onClick, onLongClick = onLong),
            contentAlignment = Alignment.Center
        ) {
            MediaThumb(v.uri, Modifier.fillMaxSize(), 400) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CookieIcon(Ic.Video, 72.dp, cs.primaryContainer, cs.onPrimaryContainer, spin = false)
                }
            }
            val tag = qTag(v.w, v.h)
            if (tag.isNotEmpty()) Box(
                Modifier.align(Alignment.TopStart).padding(10.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 9.dp, vertical = 4.dp)
            ) { Text(tag, color = Color.White, style = MaterialTheme.typography.labelSmall) }
            if (v.dur > 0) Box(
                Modifier.align(Alignment.BottomEnd).padding(start = 10.dp, end = 10.dp, bottom = if (pos > 0) 14.dp else 10.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 9.dp, vertical = 4.dp)
            ) { Text(fmtMs(v.dur), color = Color.White, style = MaterialTheme.typography.labelSmall) }
            if (pos > 0 && v.dur > 0) ProgressLine(pos.toFloat() / v.dur, Modifier.align(Alignment.BottomCenter))
            if (selected) {
                Box(Modifier.fillMaxSize().background(cs.primary.copy(alpha = 0.35f)))
                Box(Modifier.align(Alignment.TopEnd).padding(10.dp).clip(CircleShape).background(cs.primary).padding(6.dp)) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = cs.onPrimary)
                }
            }
        }
        Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(fmtSize(v.size), Modifier.weight(1f).padding(horizontal = 4.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            IconButton(onFav, Modifier.size(36.dp)) {
                Icon(if (fav) Ic.HeartFill else Ic.Heart, "مفضلة", Modifier.size(20.dp), tint = if (fav) cs.primary else cs.onSurfaceVariant)
            }
            IconButton(onShare, Modifier.size(36.dp)) { Icon(Icons.Rounded.Share, "مشاركة", Modifier.size(20.dp)) }
            IconButton(onDelete, Modifier.size(36.dp)) { Icon(Icons.Rounded.Delete, "حذف", Modifier.size(20.dp)) }
        }
    }
}
