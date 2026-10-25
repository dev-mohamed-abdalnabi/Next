package app.flowplay

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// أيقونات أزرار التحكم الرئيسية في مشغّل الفيديو: مليانة، سميكة، وحوافها مدوّرة
private fun pRR(b: PathBuilder, x: Float, y: Float, w: Float, h: Float, r: Float) = b.run {
    moveTo(x + r, y); horizontalLineTo(x + w - r)
    arcTo(r, r, 0f, false, true, x + w, y + r); verticalLineTo(y + h - r)
    arcTo(r, r, 0f, false, true, x + w - r, y + h); horizontalLineTo(x + r)
    arcTo(r, r, 0f, false, true, x, y + h - r); verticalLineTo(y + r)
    arcTo(r, r, 0f, false, true, x + r, y); close()
}

private fun pIcon(name: String, fill: Boolean, stroke: Float, build: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = if (fill) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black), strokeLineWidth = stroke,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = build
        )
    }.build()

object PIc {
    val Play: ImageVector by lazy {
        pIcon("p_play", true, 2.6f) { moveTo(8.8f, 6.4f); lineTo(18f, 12f); lineTo(8.8f, 17.6f); close() }
    }
    val Pause: ImageVector by lazy {
        pIcon("p_pause", true, 0.6f) { pRR(this, 6.6f, 5.6f, 4f, 12.8f, 2f); pRR(this, 13.4f, 5.6f, 4f, 12.8f, 2f) }
    }
    val Next: ImageVector by lazy {
        pIcon("p_next", true, 2.4f) {
            moveTo(6.2f, 6.8f); lineTo(15f, 12f); lineTo(6.2f, 17.2f); close()
            pRR(this, 16.6f, 5.8f, 2.8f, 12.4f, 1.4f)
        }
    }
    val Prev: ImageVector by lazy {
        pIcon("p_prev", true, 2.4f) {
            moveTo(17.8f, 6.8f); lineTo(9f, 12f); lineTo(17.8f, 17.2f); close()
            pRR(this, 4.6f, 5.8f, 2.8f, 12.4f, 1.4f)
        }
    }
    val Replay: ImageVector by lazy {
        pIcon("p_replay", false, 2.6f) {
            moveTo(19f, 12f); arcTo(7f, 7f, 0f, true, true, 16.5f, 6.6f)
            moveTo(19f, 4f); verticalLineTo(8f); horizontalLineTo(15f)
        }
    }
}
