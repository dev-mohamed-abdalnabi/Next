@file:OptIn(ExperimentalMaterial3Api::class)

package app.flowplay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
private fun Dot(color: Color?, selected: Boolean, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(46.dp).clip(CircleShape)
                .background(
                    if (color != null) Brush.linearGradient(listOf(color, color))
                    else Brush.sweepGradient(listOf(Color(0xFF4285F4), Color(0xFF34A853), Color(0xFFFBBC05), Color(0xFFEA4335), Color(0xFF4285F4)))
                )
                .then(if (selected) Modifier.border(3.dp, cs.onSurface, CircleShape) else Modifier)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(22.dp), tint = Color.White) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
}

@Composable
fun SettingsScreen(bottom: Dp) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Throwable) { "" } }
    val seeks = listOf(5, 10, 15, 30)
    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f)
    val mins = listOf(0, 30, 60)
    val names = listOf("أزرق", "أخضر", "بنفسجي", "برتقالي", "وردي")
    val intros = listOf(30, 60, 85, 120)
    var confirm by remember { mutableIntStateOf(0) }   // 1 مسح سجل المشاهدة، 2 مسح سجل الموسيقى

    PageList(bottom) {
        item { Header("الإعدادات") }

        item { SectionTitle("المظهر", Modifier.padding(top = 0.dp)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(Icons.Rounded.Settings, "السمة")
                Switcher(listOf("النظام", "فاتح", "داكن"), Prefs.theme) { Prefs.theme = it; Prefs.save() }
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingRow(Ic.Album, "لون التطبيق")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (android.os.Build.VERSION.SDK_INT >= 31) Dot(null, Prefs.accent == 0, "الخلفية") { Prefs.accent = 0; Prefs.save() }
                    AccentDots.forEachIndexed { i, c -> Dot(c, Prefs.accent == i + 1 || (Prefs.accent == 0 && android.os.Build.VERSION.SDK_INT < 31 && i == 0), names[i]) { Prefs.accent = i + 1; Prefs.save() } }
                }
            }
        }

        item {
            SettingRow(Ic.Bright, "وضع AMOLED", "خلفية سودا تمامًا في الوضع الداكن (توفير بطارية)", onClick = { Prefs.amoled = !Prefs.amoled; Prefs.save() }) {
                Toggle(Prefs.amoled) { Prefs.amoled = it; Prefs.save() }
            }
        }

        item { SectionTitle("الفيديو") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(Ic.Video, "شكل قائمة الفيديوهات")
                Switcher(listOf("شبكة", "قائمة يوتيوب"), Prefs.videoListStyle) { Prefs.videoListStyle = it; Prefs.save() }
            }
        }
        item {
            SettingRow(Ic.Play, "كمّل من مكان ما وقفت", "الفيديو بيرجع لآخر نقطة شاهدتها", onClick = { Prefs.resume = !Prefs.resume; Prefs.save() }) {
                Toggle(Prefs.resume) { Prefs.resume = it; Prefs.save() }
            }
        }
        item {
            SettingRow(Ic.Next, "تشغيل الفيديو التالي تلقائي", "لو مقفولة بيقف في آخر كل فيديو", onClick = { Prefs.autoNext = !Prefs.autoNext; Prefs.save() }) {
                Toggle(Prefs.autoNext) { Prefs.autoNext = it; Prefs.save() }
            }
        }
        item {
            SettingRow(Ic.Bright, "لمسات الشاشة", "اسحب يمين للصوت، شمال للسطوع، وأفقي للتقديم", onClick = { Prefs.gestures = !Prefs.gestures; Prefs.save() }) {
                Toggle(Prefs.gestures) { Prefs.gestures = it; Prefs.save() }
            }
        }
        item {
            SettingRow(Ic.Speed, "ضغطة مطوّلة = سرعة 2x", "امسك الشاشة عشان تسرّع وسيب عشان ترجع", onClick = { Prefs.longSpeed = !Prefs.longSpeed; Prefs.save() }) {
                Toggle(Prefs.longSpeed) { Prefs.longSpeed = it; Prefs.save() }
            }
        }
        item {
            SettingRow(Ic.Rotate, "لفّ الشاشة تلقائي", "أفقي للفيديو العريض وطولي للفيديو الطولي", onClick = { Prefs.autoRotate = !Prefs.autoRotate; Prefs.save() }) {
                Toggle(Prefs.autoRotate) { Prefs.autoRotate = it; Prefs.save() }
            }
        }
        item {
            SettingRow(Ic.Pip, "نافذة عايمة عند الخروج", "الفيديو يكمّل في نافذة صغيرة لما تخرج", onClick = { Prefs.pipAuto = !Prefs.pipAuto; Prefs.save() }) {
                Toggle(Prefs.pipAuto) { Prefs.pipAuto = it; Prefs.save() }
            }
        }
        item {
            SettingRow(Ic.Bright, "إبقاء الشاشة منوّرة", "أثناء مشاهدة الفيديو", onClick = { Prefs.keepOn = !Prefs.keepOn; Prefs.save() }) {
                Toggle(Prefs.keepOn) { Prefs.keepOn = it; Prefs.save() }
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(Ic.Forward, "مدة الضغط المزدوج")
                Switcher(seeks.map { "$it ث" }, seeks.indexOf(Prefs.seekSec).coerceAtLeast(0)) { Prefs.seekSec = seeks[it]; Prefs.save() }
            }
        }
        item {
            SettingRow(Ic.Play, "ضغطتين في النص = تشغيل/إيقاف", "ضغطتين على الشاشة من النص بيوقف أو يشغّل الفيديو", onClick = { Prefs.dtCenter = !Prefs.dtCenter; Prefs.save() }) {
                Toggle(Prefs.dtCenter) { Prefs.dtCenter = it; Prefs.save() }
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(Ic.Forward, "زر تخطي المقدمة")
                Switcher(intros.map { "$it ث" }, intros.indexOf(Prefs.skipIntro).coerceAtLeast(0)) { Prefs.skipIntro = intros[it]; Prefs.save() }
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(Ic.Aspect, "حجم الصورة الافتراضي")
                Switcher(listOf("مناسب", "ملء", "قص"), Prefs.aspect) { Prefs.aspect = it; Prefs.save() }
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(Ic.Speed, "السرعة الافتراضية")
                Switcher(speeds.map { if (it == 1f) "1x" else "${it}x" }, speeds.indexOf(Prefs.defaultSpeed).coerceAtLeast(0)) { Prefs.defaultSpeed = speeds[it]; Prefs.save() }
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(Ic.Subs, "حجم الترجمة")
                Switcher(listOf("صغير", "وسط", "كبير"), Prefs.subSize) { Prefs.subSize = it; Prefs.save() }
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(Ic.Subs, "لون الترجمة")
                Switcher(listOf("أبيض", "أصفر", "سماوي"), Prefs.subColor) { Prefs.subColor = it; Prefs.save() }
            }
        }
        item {
            SettingRow(Ic.Subs, "خلفية للترجمة", "خلفية سودا شفافة ورا الكلام", onClick = { Prefs.subBg = !Prefs.subBg; Prefs.save() }) {
                Toggle(Prefs.subBg) { Prefs.subBg = it; Prefs.save() }
            }
        }

        item { SectionTitle("الموسيقى") }
        item {
            SettingRow(Ic.Music, "كمّل آخر قائمة تشغيل", "لما تفتح التطبيق ترجع للأغنية اللي كنت واقف عندها", onClick = { Prefs.restoreQueue = !Prefs.restoreQueue; Prefs.save() }) {
                Toggle(Prefs.restoreQueue) { Prefs.restoreQueue = it; Prefs.save() }
            }
        }
        item {
            SettingRow(Ic.Forward, "تخطي الصمت", "بيتخطى الفراغات الصامتة في الأغاني والكتب الصوتية", onClick = { Music.setSkipSilence(!Prefs.skipSilence) }) {
                Toggle(Prefs.skipSilence) { Music.setSkipSilence(it) }
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(Ic.Timer, "إخفاء الملفات القصيرة")
                Switcher(listOf("بدون", "أقل من 30ث", "أقل من 60ث"), mins.indexOf(Prefs.minAudioSec).coerceAtLeast(0)) {
                    Prefs.minAudioSec = mins[it]; Prefs.save(); Media.refresh(ctx)
                }
                Footnote("بيخفي الرنّات والتسجيلات القصيرة من المكتبة.")
            }
        }

        item { SectionTitle("عام") }
        item {
            SettingRow(Ic.Refresh, "إعادة فحص الملفات", "لو ضفت ملفات جديدة ومظهرتش", onClick = {
                Media.refresh(ctx); Toast.makeText(ctx, "جاري الفحص…", Toast.LENGTH_SHORT).show()
            })
        }
        item {
            val vids = Media.videos; val sgs = Media.songs
            SettingRow(
                Ic.Folder, "إحصائيات المكتبة",
                "${vids.size} فيديو (${fmtSize(vids.sumOf { it.size })})  •  ${sgs.size} أغنية (${fmtMs(sgs.sumOf { it.dur })})"
            )
        }
        item {
            SettingRow(Ic.Link, "نسخ احتياطي", "بينسخ المفضلة والقوائم للحافظة", onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Next", Store.exportAll()))
                Toast.makeText(ctx, "اتنسخ للحافظة. الصقه في أي مكان واحتفظ بيه", Toast.LENGTH_LONG).show()
            })
        }
        item {
            SettingRow(Ic.Plus, "استرجاع من الحافظة", "الصق النسخة الاحتياطية اللي نسختها قبل كده", onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val t = try { cm.primaryClip?.getItemAt(0)?.text?.toString() ?: "" } catch (_: Throwable) { "" }
                val ok = t.isNotBlank() && Store.importAll(t)
                Toast.makeText(ctx, if (ok) "تم الاسترجاع" else "مفيش نسخة احتياطية صالحة في الحافظة", Toast.LENGTH_SHORT).show()
            })
        }
        item { SettingRow(Ic.Video, "مسح سجل المشاهدة", "بيشيل أماكن التوقف و\"أكمل المشاهدة\"", onClick = { confirm = 1 }) }
        item { SettingRow(Ic.Music, "مسح سجل الموسيقى", "بيصفّر عدّاد التشغيل والأغاني الأخيرة", onClick = { confirm = 2 }) }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Next", style = MaterialTheme.typography.titleMedium, color = cs.primary)
                Text("الإصدار $ver", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }

    if (confirm != 0) AlertDialog(
        onDismissRequest = { confirm = 0 },
        title = { Text(if (confirm == 1) "مسح سجل المشاهدة؟" else "مسح سجل الموسيقى؟") },
        text = { Text("مش هتقدر ترجّعه تاني.") },
        confirmButton = {
            TextButton({
                if (confirm == 1) Store.clearWatchHistory() else Store.clearPlayHistory()
                confirm = 0
                Toast.makeText(ctx, "تم المسح", Toast.LENGTH_SHORT).show()
            }) { Text("مسح", color = cs.error) }
        },
        dismissButton = { TextButton({ confirm = 0 }) { Text("إلغاء") } }
    )
}
