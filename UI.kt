@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.flowplay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ═══════════════════════════ الثيم ═══════════════════════════
// ألوان ديناميكية من خلفية الموبايل (أندرويد 12+)، وألوان احتياطية للأقدم (نفس ألوان Grab)

private val LightFallback = lightColorScheme(
    primary = Color(0xFF0B57D0), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD), onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF565F71), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2F9), onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF146C2E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC4EED0), onTertiaryContainer = Color(0xFF072711),
    background = Color(0xFFF8F9FF), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF), onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC), onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F), outlineVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F3F9),
    surfaceContainer = Color(0xFFECEEF4), surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E8),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002)
)

private val DarkFallback = darkColorScheme(
    primary = Color(0xFFA8C7FA), onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0), onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBDC7DC), onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3F4759), onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFF6DD58C), onTertiary = Color(0xFF003915),
    tertiaryContainer = Color(0xFF0F5223), onTertiaryContainer = Color(0xFFC4EED0),
    background = Color(0xFF111318), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E), onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099), outlineVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024), surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

// خط Tajawal (الملفات جاهزة في فولدر fonts والـWorkflow بينسخها في res/font)
private val AppFont = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold)
)
private fun TextStyle.f(w: FontWeight? = null) = copy(fontFamily = AppFont, fontWeight = w ?: fontWeight)

private val T0 = Typography()
private val AppType = Typography(
    displayLarge = T0.displayLarge.f(FontWeight.ExtraBold),
    displayMedium = T0.displayMedium.f(FontWeight.ExtraBold),
    displaySmall = T0.displaySmall.copy(fontSize = 36.sp, lineHeight = 52.sp).f(FontWeight.ExtraBold),
    headlineLarge = T0.headlineLarge.f(FontWeight.ExtraBold),
    headlineMedium = T0.headlineMedium.f(FontWeight.ExtraBold),
    headlineSmall = T0.headlineSmall.f(FontWeight.Bold),
    titleLarge = T0.titleLarge.f(FontWeight.Bold),
    titleMedium = T0.titleMedium.f(FontWeight.Bold),
    titleSmall = T0.titleSmall.f(FontWeight.Bold),
    bodyLarge = T0.bodyLarge.f(FontWeight.Normal),
    bodyMedium = T0.bodyMedium.f(FontWeight.Normal),
    bodySmall = T0.bodySmall.f(FontWeight.Normal),
    labelLarge = T0.labelLarge.f(FontWeight.Bold),
    labelMedium = T0.labelMedium.f(FontWeight.Medium),
    labelSmall = T0.labelSmall.f(FontWeight.Medium)
)

// ألوان التمييز البديلة: أزرق، أخضر، بنفسجي، برتقالي، وردي
val AccentDots = listOf(Color(0xFF0B57D0), Color(0xFF1B6C3A), Color(0xFF6750A4), Color(0xFF9A4600), Color(0xFFA8305F))

private fun accentScheme(a: Int, dark: Boolean): ColorScheme {
    val base = if (dark) DarkFallback else LightFallback
    return when (a) {
        2 -> if (dark) base.copy(primary = Color(0xFF8FD8A0), onPrimary = Color(0xFF00391A), primaryContainer = Color(0xFF005226), onPrimaryContainer = Color(0xFFB5F2C4))
             else base.copy(primary = Color(0xFF1B6C3A), primaryContainer = Color(0xFFB5F2C4), onPrimaryContainer = Color(0xFF00210D))
        3 -> if (dark) base.copy(primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72), primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF))
             else base.copy(primary = Color(0xFF6750A4), primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D))
        4 -> if (dark) base.copy(primary = Color(0xFFFFB68A), onPrimary = Color(0xFF512300), primaryContainer = Color(0xFF743400), onPrimaryContainer = Color(0xFFFFDBC8))
             else base.copy(primary = Color(0xFF9A4600), primaryContainer = Color(0xFFFFDBC8), onPrimaryContainer = Color(0xFF331200))
        5 -> if (dark) base.copy(primary = Color(0xFFFFB0CB), onPrimary = Color(0xFF640032), primaryContainer = Color(0xFF86144A), onPrimaryContainer = Color(0xFFFFD9E3))
             else base.copy(primary = Color(0xFFA8305F), primaryContainer = Color(0xFFFFD9E3), onPrimaryContainer = Color(0xFF3E001D))
        else -> base
    }
}

@Composable
fun AppTheme(forceDark: Boolean = false, content: @Composable () -> Unit) {
    val dark = forceDark || when (Prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val cs = when {
        Prefs.accent == 0 && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> accentScheme(Prefs.accent, dark)
    }.let { base ->
        if (dark && Prefs.amoled) base.copy(
            background = Color.Black, surface = Color.Black,
            surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF121212), surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF222222)
        ) else base
    }
    // الواجهة عربي دايمًا (من اليمين لليسار) بغض النظر عن لغة الجهاز
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = cs, typography = AppType, content = content)
    }
}

// ═══════════════════════════ الأيقونات المرسومة ═══════════════════════════
// طقم أيقونات جديد: خطوط رفيعة موحّدة (2dp) بأطراف مدوّرة، ومعاها أشكال مليانة مدوّرة للتحكّم في التشغيل

private class Part(val fill: Boolean, val w: Float, val b: PathBuilder.() -> Unit)

private fun outl(w: Float = 2f, b: PathBuilder.() -> Unit) = Part(false, w, b)
private fun solid(w: Float = 1.6f, b: PathBuilder.() -> Unit) = Part(true, w, b)

private fun vec(name: String, fill: Boolean = false, w: Float = 2f, mirror: Boolean = false, b: PathBuilder.() -> Unit): ImageVector =
    vecM(name, mirror, Part(fill, w, b))

private fun vecM(name: String, mirror: Boolean = false, vararg parts: Part): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror).apply {
        parts.forEach { p ->
            path(
                fill = if (p.fill) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black), strokeLineWidth = p.w,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = p.b
            )
        }
    }.build()

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
}

// مستطيل بحواف مدوّرة
private fun PathBuilder.rr(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y); horizontalLineTo(x + w - r)
    arcTo(r, r, 0f, false, true, x + w, y + r); verticalLineTo(y + h - r)
    arcTo(r, r, 0f, false, true, x + w - r, y + h); horizontalLineTo(x + r)
    arcTo(r, r, 0f, false, true, x, y + h - r); verticalLineTo(y + r)
    arcTo(r, r, 0f, false, true, x + r, y); close()
}

object Ic {
    // ── التحكّم في التشغيل: أشكال مليانة بحواف مدوّرة ──
    val Play by lazy { vec("play", fill = true, w = 3f) {
        moveTo(8.7f, 6.7f); lineTo(17.4f, 12f); lineTo(8.7f, 17.3f); close()
    } }
    val Pause by lazy { vecM("pause", false,
        solid(2.4f) { rr(7.4f, 6.2f, 2.2f, 11.6f, 1.1f); rr(14.4f, 6.2f, 2.2f, 11.6f, 1.1f) }
    ) }
    val Next by lazy { vecM("next", false,
        solid(2.4f) { moveTo(6.8f, 7.6f); lineTo(14f, 12f); lineTo(6.8f, 16.4f); close() },
        outl(2.6f) { moveTo(17.8f, 6.8f); verticalLineTo(17.2f) }
    ) }
    val Prev by lazy { vecM("prev", false,
        solid(2.4f) { moveTo(17.2f, 7.6f); lineTo(10f, 12f); lineTo(17.2f, 16.4f); close() },
        outl(2.6f) { moveTo(6.2f, 6.8f); verticalLineTo(17.2f) }
    ) }
    val Forward by lazy { vec("forward", fill = true, w = 2.2f) {
        moveTo(5.6f, 7.6f); lineTo(11.2f, 12f); lineTo(5.6f, 16.4f); close()
        moveTo(12.8f, 7.6f); lineTo(18.4f, 12f); lineTo(12.8f, 16.4f); close()
    } }
    val Backward by lazy { vec("backward", fill = true, w = 2.2f) {
        moveTo(18.4f, 7.6f); lineTo(12.8f, 12f); lineTo(18.4f, 16.4f); close()
        moveTo(11.2f, 7.6f); lineTo(5.6f, 12f); lineTo(11.2f, 16.4f); close()
    } }
    val Shuffle by lazy { vec("shuffle") {
        moveTo(3f, 17.5f); horizontalLineTo(5.5f); curveTo(8.5f, 17.5f, 10f, 16f, 11.5f, 13.8f)
        lineTo(12.8f, 11.8f); curveTo(14.3f, 9.6f, 15.8f, 8.5f, 18.5f, 8.5f); horizontalLineTo(21f)
        moveTo(18.6f, 5.8f); lineTo(21.3f, 8.5f); lineTo(18.6f, 11.2f)
        moveTo(3f, 6.5f); horizontalLineTo(5.5f); curveTo(7.6f, 6.5f, 9f, 7.3f, 10f, 8.8f)
        moveTo(14f, 15.2f); curveTo(15.2f, 16.8f, 16.5f, 17.5f, 18.5f, 17.5f); horizontalLineTo(21f)
        moveTo(18.6f, 14.8f); lineTo(21.3f, 17.5f); lineTo(18.6f, 20.2f)
    } }
    val Repeat by lazy { vec("repeat") {
        moveTo(4f, 12f); verticalLineTo(11f); arcTo(3.5f, 3.5f, 0f, false, true, 7.5f, 7.5f); horizontalLineTo(20f)
        moveTo(17.2f, 4.7f); lineTo(20f, 7.5f); lineTo(17.2f, 10.3f)
        moveTo(20f, 12f); verticalLineTo(13f); arcTo(3.5f, 3.5f, 0f, false, true, 16.5f, 16.5f); horizontalLineTo(4f)
        moveTo(6.8f, 13.7f); lineTo(4f, 16.5f); lineTo(6.8f, 19.3f)
    } }
    val RepeatOne by lazy { vecM("repeat_one", false,
        outl { 
            moveTo(4f, 12f); verticalLineTo(11f); arcTo(3.5f, 3.5f, 0f, false, true, 7.5f, 7.5f); horizontalLineTo(20f)
            moveTo(17.2f, 4.7f); lineTo(20f, 7.5f); lineTo(17.2f, 10.3f)
            moveTo(20f, 12f); verticalLineTo(13f); arcTo(3.5f, 3.5f, 0f, false, true, 16.5f, 16.5f); horizontalLineTo(4f)
            moveTo(6.8f, 13.7f); lineTo(4f, 16.5f); lineTo(6.8f, 19.3f)
        },
        outl(1.8f) { moveTo(10.8f, 10.8f); lineTo(12.4f, 9.8f); verticalLineTo(14.2f) }
    ) }

    // ── الوسائط ──
    val Music by lazy { vecM("music", false,
        outl { moveTo(9.5f, 17f); verticalLineTo(6.8f); lineTo(19f, 4.8f); verticalLineTo(15f) },
        solid(1.4f) { circle(7f, 17.3f, 2.4f); circle(16.5f, 15.3f, 2.4f) }
    ) }
    val Video by lazy { vecM("video", false,
        outl { rr(3f, 5.5f, 18f, 13f, 3.8f) },
        solid(1.6f) { moveTo(10.6f, 9.8f); lineTo(14.6f, 12f); lineTo(10.6f, 14.2f); close() }
    ) }
    val Queue by lazy { vecM("queue", false,
        outl { moveTo(4f, 6.5f); horizontalLineTo(15.5f); moveTo(4f, 11.5f); horizontalLineTo(15.5f); moveTo(4f, 16.5f); horizontalLineTo(9.5f) },
        solid(1.6f) { moveTo(14.8f, 14f); lineTo(20f, 17.2f); lineTo(14.8f, 20.4f); close() }
    ) }
    val Subs by lazy { vecM("subs", false,
        outl { rr(3f, 5.5f, 18f, 13f, 3.8f) },
        outl { moveTo(7f, 10.8f); horizontalLineTo(10f); moveTo(13f, 10.8f); horizontalLineTo(17f)
             moveTo(7f, 14.2f); horizontalLineTo(12f); moveTo(15f, 14.2f); horizontalLineTo(17f) }
    ) }
    val Headphones by lazy { vecM("headphones", false,
        outl { moveTo(4.5f, 14.5f); verticalLineTo(12f); arcTo(7.5f, 7.5f, 0f, false, true, 19.5f, 12f); verticalLineTo(14.5f) },
        solid(1.6f) { rr(3.8f, 13.6f, 3.4f, 6f, 1.7f); rr(16.8f, 13.6f, 3.4f, 6f, 1.7f) }
    ) }
    val Pip by lazy { vecM("pip", false,
        outl { rr(3f, 5.5f, 18f, 13f, 3.8f) },
        solid(1.4f) { rr(12.4f, 11.6f, 5.6f, 3.6f, 1.2f) }
    ) }
    val Folder by lazy { vec("folder") {
        moveTo(3f, 8.5f); verticalLineTo(6.5f); arcTo(2f, 2f, 0f, false, true, 5f, 4.5f); horizontalLineTo(9.2f)
        lineTo(11.6f, 7.5f); horizontalLineTo(19f); arcTo(2f, 2f, 0f, false, true, 21f, 9.5f); verticalLineTo(17.5f)
        arcTo(2f, 2f, 0f, false, true, 19f, 19.5f); horizontalLineTo(5f); arcTo(2f, 2f, 0f, false, true, 3f, 17.5f); close()
    } }
    val Album by lazy { vecM("album", false,
        outl { circle(12f, 12f, 9f) },
        outl(1.6f) { circle(12f, 12f, 4.2f) },
        solid(0.6f) { circle(12f, 12f, 1.1f) }
    ) }
    val Artist by lazy { vec("artist") {
        circle(12f, 8f, 4f)
        moveTo(4.5f, 20f); arcTo(7.5f, 6.5f, 0f, false, true, 19.5f, 20f)
    } }

    // ── أدوات المشغّل ──
    val Speed by lazy { vecM("speed", false,
        outl { moveTo(4f, 17.5f); arcTo(8f, 8f, 0f, false, true, 20f, 17.5f)
             moveTo(12f, 17.5f); lineTo(15.8f, 11.5f) },
        solid(0.8f) { circle(12f, 17.5f, 1.3f) }
    ) }
    val Timer by lazy { vec("timer") {
        circle(12f, 13.5f, 7.5f)
        moveTo(12f, 9.8f); verticalLineTo(13.5f); lineTo(14.6f, 15.2f)
        moveTo(10f, 2.8f); horizontalLineTo(14f)
    } }
    val Eq by lazy { vec("eq") {
        moveTo(6f, 4f); verticalLineTo(6.5f); moveTo(6f, 11.5f); verticalLineTo(20f); circle(6f, 9f, 2.5f)
        moveTo(12f, 4f); verticalLineTo(12.5f); moveTo(12f, 17.5f); verticalLineTo(20f); circle(12f, 15f, 2.5f)
        moveTo(18f, 4f); verticalLineTo(5.5f); moveTo(18f, 10.5f); verticalLineTo(20f); circle(18f, 8f, 2.5f)
    } }
    val Volume by lazy { vecM("volume", false,
        solid(2f) { moveTo(4.5f, 10.2f); horizontalLineTo(7.6f); lineTo(12f, 6.6f); verticalLineTo(17.4f); lineTo(7.6f, 13.8f); horizontalLineTo(4.5f); close() },
        outl { moveTo(15.6f, 9.2f); quadTo(17.6f, 12f, 15.6f, 14.8f)
             moveTo(18.4f, 6.4f); quadTo(22.2f, 12f, 18.4f, 17.6f) }
    ) }
    val Bright by lazy { vec("bright") {
        circle(12f, 12f, 4f)
        moveTo(12f, 3f); verticalLineTo(5f); moveTo(12f, 19f); verticalLineTo(21f)
        moveTo(3f, 12f); horizontalLineTo(5f); moveTo(19f, 12f); horizontalLineTo(21f)
        moveTo(5.6f, 5.6f); lineTo(7f, 7f); moveTo(17f, 17f); lineTo(18.4f, 18.4f)
        moveTo(18.4f, 5.6f); lineTo(17f, 7f); moveTo(7f, 17f); lineTo(5.6f, 18.4f)
    } }
    val Lock by lazy { vecM("lock", false,
        outl { rr(5f, 10.5f, 14f, 10f, 3f); moveTo(8f, 10.5f); verticalLineTo(8f); arcTo(4f, 4f, 0f, false, true, 16f, 8f); verticalLineTo(10.5f) },
        solid(0.6f) { circle(12f, 15.5f, 1.2f) }
    ) }
    val Unlock by lazy { vecM("unlock", false,
        outl { rr(5f, 10.5f, 14f, 10f, 3f); moveTo(8f, 10.5f); verticalLineTo(8f); arcTo(4f, 4f, 0f, false, true, 15.8f, 7f) },
        solid(0.6f) { circle(12f, 15.5f, 1.2f) }
    ) }
    val Aspect by lazy { vec("aspect") {
        moveTo(4f, 9f); verticalLineTo(7f); arcTo(3f, 3f, 0f, false, true, 7f, 4f); horizontalLineTo(9f)
        moveTo(15f, 4f); horizontalLineTo(17f); arcTo(3f, 3f, 0f, false, true, 20f, 7f); verticalLineTo(9f)
        moveTo(20f, 15f); verticalLineTo(17f); arcTo(3f, 3f, 0f, false, true, 17f, 20f); horizontalLineTo(15f)
        moveTo(9f, 20f); horizontalLineTo(7f); arcTo(3f, 3f, 0f, false, true, 4f, 17f); verticalLineTo(15f)
    } }
    val Rotate by lazy { vec("rotate") {
        moveTo(19f, 12f); arcTo(7f, 7f, 0f, true, true, 12f, 5f)
        moveTo(14.2f, 2.6f); lineTo(11.8f, 5f); lineTo(14.2f, 7.4f)
    } }

    // ── تفاعل وتنقّل ──
    val Heart by lazy { vec("heart") {
        moveTo(12f, 20.2f); curveTo(5f, 15f, 3f, 11.2f, 3f, 8.6f); curveTo(3f, 5.7f, 5.2f, 4f, 7.6f, 4f)
        curveTo(9.5f, 4f, 11f, 5f, 12f, 6.8f); curveTo(13f, 5f, 14.5f, 4f, 16.4f, 4f)
        curveTo(18.8f, 4f, 21f, 5.7f, 21f, 8.6f); curveTo(21f, 11.2f, 19f, 15f, 12f, 20.2f); close()
    } }
    val HeartFill by lazy { vec("heart_fill", fill = true) {
        moveTo(12f, 20.2f); curveTo(5f, 15f, 3f, 11.2f, 3f, 8.6f); curveTo(3f, 5.7f, 5.2f, 4f, 7.6f, 4f)
        curveTo(9.5f, 4f, 11f, 5f, 12f, 6.8f); curveTo(13f, 5f, 14.5f, 4f, 16.4f, 4f)
        curveTo(18.8f, 4f, 21f, 5.7f, 21f, 8.6f); curveTo(21f, 11.2f, 19f, 15f, 12f, 20.2f); close()
    } }
    val More by lazy { vec("more", w = 3.4f) {
        moveTo(12f, 5f); lineTo(12.01f, 5f)
        moveTo(12f, 12f); lineTo(12.01f, 12f)
        moveTo(12f, 19f); lineTo(12.01f, 19f)
    } }
    val Sort by lazy { vec("sort") {
        moveTo(4f, 6.5f); horizontalLineTo(20f)
        moveTo(4f, 12f); horizontalLineTo(15f)
        moveTo(4f, 17.5f); horizontalLineTo(10f)
    } }
    val Plus by lazy { vec("plus", w = 2.2f) {
        moveTo(12f, 5f); verticalLineTo(19f)
        moveTo(5f, 12f); horizontalLineTo(19f)
    } }
    val Back by lazy { vec("back", w = 2.2f, mirror = true) {
        moveTo(14.5f, 5.5f); lineTo(8f, 12f); lineTo(14.5f, 18.5f)
    } }
    val Down by lazy { vec("down", w = 2.2f) {
        moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f)
    } }
    val Link by lazy { vec("link") {
        moveTo(10f, 7f); horizontalLineTo(7f); arcTo(5f, 5f, 0f, false, false, 7f, 17f); horizontalLineTo(10f)
        moveTo(14f, 7f); horizontalLineTo(17f); arcTo(5f, 5f, 0f, false, true, 17f, 17f); horizontalLineTo(14f)
        moveTo(8.5f, 12f); horizontalLineTo(15.5f)
    } }
    val Refresh by lazy { vec("refresh") {
        moveTo(19f, 12f); arcTo(7f, 7f, 0f, true, true, 16.5f, 6.6f)
        moveTo(19f, 4f); verticalLineTo(8f); horizontalLineTo(15f)
    } }

    // ── أيقونات كانت من مكتبة Material وبقت من نفس الطقم ──
    val Search by lazy { vec("search", w = 2.2f) {
        circle(10.5f, 10.5f, 6.3f)
        moveTo(15.2f, 15.2f); lineTo(20f, 20f)
    } }
    val Close by lazy { vec("close", w = 2.2f) {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    } }
    val Check by lazy { vec("check", w = 2.4f) {
        moveTo(5f, 12.5f); lineTo(9.8f, 17.2f); lineTo(19f, 7.2f)
    } }
    val Menu by lazy { vec("menu", w = 2.2f) {
        moveTo(4f, 7f); horizontalLineTo(20f)
        moveTo(4f, 12f); horizontalLineTo(20f)
        moveTo(4f, 17f); horizontalLineTo(20f)
    } }
    val Share by lazy { vec("share") {
        circle(6.5f, 12f, 2.5f); circle(17.5f, 6f, 2.5f); circle(17.5f, 18f, 2.5f)
        moveTo(8.8f, 10.8f); lineTo(15.2f, 7.2f)
        moveTo(8.8f, 13.2f); lineTo(15.2f, 16.8f)
    } }
    val Delete by lazy { vec("delete") {
        moveTo(4f, 7f); horizontalLineTo(20f)
        moveTo(9f, 7f); verticalLineTo(5f); horizontalLineTo(15f); verticalLineTo(7f)
        moveTo(6f, 7f); lineTo(6.8f, 18f); arcTo(2f, 2f, 0f, false, false, 8.8f, 20f); horizontalLineTo(15.2f)
        arcTo(2f, 2f, 0f, false, false, 17.2f, 18f); lineTo(18f, 7f)
        moveTo(10f, 11f); verticalLineTo(16f); moveTo(14f, 11f); verticalLineTo(16f)
    } }
    val Settings by lazy { vec("settings") {
        moveTo(4f, 8f); horizontalLineTo(11.5f); circle(14f, 8f, 2.5f); moveTo(16.5f, 8f); horizontalLineTo(20f)
        moveTo(4f, 16f); horizontalLineTo(7f); circle(9.5f, 16f, 2.5f); moveTo(12f, 16f); horizontalLineTo(20f)
    } }
}

// ═══════════════════════════ أشكال وحركة ═══════════════════════════

// شكل "الكوكي" المتموّج: دايرة حوافها فصوص ناعمة
class CookieShape(private val lobes: Int = 9, private val amp: Float = 0.06f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(size.width, size.height) / 2f
        val cx = size.width / 2f; val cy = size.height / 2f
        val p = Path(); val steps = 180
        for (i in 0..steps) {
            val a = (2.0 * PI * i / steps).toFloat()
            val rad = r * (1f - amp + amp * cos(lobes * a))
            val x = cx + rad * cos(a); val y = cy + rad * sin(a)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return Outline.Generic(p)
    }
}

@Composable
fun CookieIcon(
    icon: ImageVector, size: Dp, container: Color, tint: Color, modifier: Modifier = Modifier, spin: Boolean = true,
    desc: String? = null, onClick: (() -> Unit)? = null
) {
    val shape = remember { CookieShape() }
    val rot by rememberInfiniteTransition(label = "cookie").animateFloat(
        0f, 360f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "rot"
    )
    val click = if (onClick != null) Modifier.clip(CircleShape).clickable(onClick = onClick) else Modifier
    Box(modifier.size(size).then(click), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = if (spin) rot else 0f }.clip(shape).background(container))
        Icon(icon, desc, Modifier.size(size * 0.44f), tint = tint)
    }
}

// شريط تقدّم متموّج قابل للسحب. يتحوّل لخط مستقيم لما الصوت يقف.
// frac: 0..1. onScrub: بيتنادى أثناء السحب (null لما يخلص). onSeek: لما تسيب إيدك أو تضغط.
@Composable
fun WavySeek(
    frac: Float, playing: Boolean, modifier: Modifier = Modifier, wave: Boolean = true,
    onScrub: (Float?) -> Unit = {}, onSeek: (Float) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = (drag ?: frac).coerceIn(0f, 1f)
    val inf = rememberInfiniteTransition(label = "wave")
    val phase by inf.animateFloat(0f, (2.0 * PI).toFloat(), infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "ph")
    val amp by animateDpAsState(if (wave && playing && drag == null) 3.dp else 0.dp, spring(), label = "amp")
    val thumbH by animateDpAsState(if (drag != null) 32.dp else 24.dp, spring(), label = "th")
    val active = cs.primary; val track = cs.secondaryContainer

    Canvas(
        modifier.fillMaxWidth().height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f); onScrub(drag) },
                    onDragEnd = { drag?.let { onSeek(it) }; drag = null; onScrub(null) },
                    onDragCancel = { drag = null; onScrub(null) },
                    onHorizontalDrag = { ch, _ ->
                        ch.consume()
                        drag = (ch.position.x / size.width).coerceIn(0f, 1f); onScrub(drag)
                    }
                )
            }
    ) {
        val sw = 4.dp.toPx(); val a = amp.toPx(); val wl = 24.dp.toPx()
        val cy = size.height / 2f; val w = size.width
        val x = w * shown
        val thumbW = 5.dp.toPx()
        val g = thumbW / 2f + sw * 0.7f
        if (x + g < w) drawLine(track, Offset(x + g, cy), Offset(w, cy), sw, StrokeCap.Round)
        val end = x - g
        if (end > 1f) {
            if (a < 0.1f) drawLine(active, Offset(0f, cy), Offset(end, cy), sw, StrokeCap.Round)
            else {
                val path = Path()
                var px = 0f
                path.moveTo(px, cy + a * sin(px / wl * 2f * PI.toFloat() - phase))
                while (px < end) {
                    px = min(px + 3f, end)
                    path.lineTo(px, cy + a * sin(px / wl * 2f * PI.toFloat() - phase))
                }
                drawPath(path, active, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        val th = thumbH.toPx()
        drawLine(active, Offset(x, cy - th / 2f), Offset(x, cy + th / 2f), thumbW, StrokeCap.Round)
    }
}

// ═══════════════════════════ صور مصغّرة ═══════════════════════════

@Composable
fun MediaThumb(uri: String?, modifier: Modifier = Modifier, px: Int = 320, fallback: (@Composable BoxScope.() -> Unit)? = null) {
    val ctx = LocalContext.current.applicationContext
    val bmp by produceState<Bitmap?>(null, uri, px) {
        value = if (uri == null) null else Thumbs.peek(uri, px)
        if (uri != null && value == null) value = withContext(Dispatchers.IO) { Thumbs.load(ctx, uri, px) }
    }
    Box(modifier) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else fallback?.invoke(this)
    }
}

// ═══════════════════════════ عناصر مشتركة ═══════════════════════════

val LocalSnack = staticCompositionLocalOf<SnackbarHostState> { error("no snackbar host") }

@Composable
fun fieldColors(): TextFieldColors {
    val c = MaterialTheme.colorScheme.surfaceContainerHigh
    return TextFieldDefaults.colors(
        focusedContainerColor = c, unfocusedContainerColor = c, disabledContainerColor = c,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent
    )
}

@Composable
fun Header(title: String, sub: String? = null) {
    Column(Modifier.statusBarsPadding().padding(top = 24.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.displaySmall)
        if (sub != null) Text(sub, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 28.dp, bottom = 6.dp))

@Composable
fun Footnote(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))

@Composable
fun SettingRow(
    icon: ImageVector?, title: String, sub: String? = null, subColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (icon != null) Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(cs.secondaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, Modifier.size(22.dp), tint = cs.onSecondaryContainer) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium,
                color = if (subColor == Color.Unspecified) cs.onSurfaceVariant else subColor)
        }
        trailing?.invoke()
    }
}

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) =
    Switch(checked, onChange, thumbContent = if (checked) ({ Icon(Ic.Check, null, Modifier.size(SwitchDefaults.IconSize)) }) else null)

// مبدّل بحبّة بتنزلق بين الخيارات
@Composable
fun Switcher(opts: List<String>, sel: Int, modifier: Modifier = Modifier, on: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxWidth().height(54.dp).clip(CircleShape).background(cs.surfaceContainerHigh).padding(4.dp)) {
        val w = maxWidth / opts.size
        val x by animateDpAsState(w * sel.coerceAtLeast(0), spring(0.8f, 420f), label = "sw")
        if (sel >= 0) Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(CircleShape).background(cs.primary))
        Row(Modifier.fillMaxSize()) {
            opts.forEachIndexed { i, o ->
                val c by animateColorAsState(if (i == sel) cs.onPrimary else cs.onSurface, label = "swc")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { on(i) },
                    contentAlignment = Alignment.Center
                ) { Text(o, color = c, style = MaterialTheme.typography.titleSmall, maxLines = 1) }
            }
        }
    }
}

// زر كبير: بيتحوّل شكله ويصغر لما تضغط عليه
@Composable
fun BigButton(
    text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true,
    loading: Boolean = false, tonal: Boolean = false, onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pressed) 18.dp else 32.dp, spring(0.55f, 600f), label = "r")
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, spring(0.55f, 600f), label = "s")
    val bg = if (tonal) cs.secondaryContainer else cs.primary
    val fg = if (tonal) cs.onSecondaryContainer else cs.onPrimary
    Row(
        modifier.fillMaxWidth().height(64.dp)
            .graphicsLayer { scaleX = s; scaleY = s; alpha = if (enabled) 1f else 0.38f }
            .clip(RoundedCornerShape(r)).background(bg)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = fg)
        else if (icon != null) Icon(icon, null, Modifier.size(24.dp), tint = fg)
        Spacer(Modifier.width(10.dp))
        Text(text, color = fg, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CookieIcon(icon, 128.dp, cs.primaryContainer, cs.onPrimaryContainer)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun PageList(bottom: Dp, state: LazyListState = rememberLazyListState(), content: LazyListScope.() -> Unit) =
    LazyColumn(
        Modifier.fillMaxSize().imePadding(), state = state,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom), content = content
    )

// ═══════════════════════════ الإذن + الهيكل + الشريط العايم ═══════════════════════════

private fun mediaPerms(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

fun hasMediaPerm(c: Context): Boolean {
    fun ok(p: String) = c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    return if (Build.VERSION.SDK_INT >= 33) ok(Manifest.permission.READ_MEDIA_VIDEO) && ok(Manifest.permission.READ_MEDIA_AUDIO)
    else ok(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        CookieIcon(Ic.Video, 148.dp, cs.primary, cs.onPrimary)
        Spacer(Modifier.height(28.dp))
        Text("Next", style = MaterialTheme.typography.titleMedium, color = cs.primary)
        Text("محتاجين نشوف ملفاتك", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "عشان نعرض الفيديوهات والأغاني اللي على جهازك. الملفات بتفضل عندك ومش بتتبعت لأي مكان.",
            style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        BigButton("السماح بالوصول", icon = Ic.Play) { onGrant() }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var granted by remember { mutableStateOf(hasMediaPerm(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasMediaPerm(ctx)
    }
    LaunchedEffect(granted) { if (granted) Media.refresh(ctx) }
    LaunchedEffect(granted, Media.loaded, Music.connected) {
        if (granted && Media.loaded && Music.connected) Music.restore()
    }
    Surface(Modifier.fillMaxSize(), color = cs.surface, contentColor = cs.onSurface) {
        if (!granted) PermissionScreen { launcher.launch(mediaPerms()) } else Shell()
    }
}

@Composable
private fun Shell() {
    val cs = MaterialTheme.colorScheme
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var np by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // الشريط المصغّر بيظهر بس جوه قسم الموسيقى (tab 1)، ومش بيظهر في الفيديو أو الإعدادات حتى لو فيه أغنية شغالة
    val mini = Music.has && tab == 1
    val bottom = if (imeOpen) 16.dp
    else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 120.dp + (if (mini) 80.dp else 0.dp)

    BackHandler(enabled = tab != 0 && !np) { tab = 0 }

    CompositionLocalProvider(LocalSnack provides snack) {
        Box(Modifier.fillMaxSize()) {
            Crossfade(tab, Modifier.fillMaxSize(), label = "tab") { t ->
                when (t) { 0 -> VideoScreen(bottom); 1 -> MusicScreen(bottom); else -> SettingsScreen(bottom) }
            }
            AnimatedVisibility(
                !imeOpen, Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, cs.surface)))
                        .navigationBarsPadding().padding(top = 28.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnimatedVisibility(mini, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                        MiniPlayer { np = true }
                    }
                    FloatingNav(tab) { tab = it }
                }
            }
            SnackbarHost(
                snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = if (imeOpen) 12.dp else if (mini) 180.dp else 100.dp)
            )
            if (np) NowPlaying { np = false }
        }
    }
}

private class NavTab(val label: String, val icon: ImageVector)

@Composable
private fun FloatingNav(sel: Int, on: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hap = LocalHapticFeedback.current
    val items = remember {
        listOf(NavTab("الفيديو", Ic.Video), NavTab("الموسيقى", Ic.Music), NavTab("الإعدادات", Ic.Settings))
    }
    Surface(shape = CircleShape, color = cs.surfaceContainerHigh, shadowElevation = 10.dp) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { i, t ->
                val a = i == sel
                val bg by animateColorAsState(if (a) cs.primaryContainer else Color.Transparent, label = "nb")
                Row(
                    Modifier.height(54.dp).clip(CircleShape).background(bg)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            hap.performHapticFeedback(HapticFeedbackType.TextHandleMove); on(i)
                        }
                        .padding(horizontal = 20.dp).animateContentSize(spring(0.8f, 400f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(t.icon, t.label, tint = if (a) cs.onPrimaryContainer else cs.onSurfaceVariant)
                    AnimatedVisibility(a, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                        Text(
                            t.label, Modifier.padding(start = 10.dp), color = cs.onPrimaryContainer,
                            style = MaterialTheme.typography.titleSmall, maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
