package app.flowplay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

// تهيئة الإعدادات والتخزين مرة واحدة (بتتنادى من أي Activity أو الخدمة)
object AppInit {
    private var done = false
    fun ensure(c: Context) {
        if (done) return
        done = true
        val app = c.applicationContext
        Prefs.load(app)
        Store.init(app)
        Music.init(app)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppInit.ensure(this)
        setContent { AppTheme { App() } }
        handleAudioIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        Music.connect()
        if (hasMediaPerm(this) && Media.loaded) Media.refresh(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAudioIntent(intent)
    }

    // فتح ملف صوت من مدير الملفات: يشتغل في المشغّل الموسيقي
    private fun handleAudioIntent(i: Intent?) {
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
}
