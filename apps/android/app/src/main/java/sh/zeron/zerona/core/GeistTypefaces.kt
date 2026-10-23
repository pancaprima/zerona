package sh.zeron.zerona.core

import android.content.Context
import android.graphics.Typeface
import uniffi.zeron_core.FaceRole

/**
 * Bundled Geist faces for layout measurement and transcript painting.
 * Must match [FontLoader] asset names.
 */
object GeistTypefaces {
    private val assetFiles: List<Pair<FaceRole, String>> = FontLoader.assetFileNames()

    private val lock = Any()
    private var initialized = false
    private val byRole = mutableMapOf<FaceRole, Typeface>()

    fun ensureLoaded(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            val assets = context.applicationContext.assets
            val missing = mutableListOf<FaceRole>()
            for ((role, name) in assetFiles) {
                try {
                    byRole[role] = Typeface.createFromAsset(assets, "fonts/$name")
                } catch (_: Exception) {
                    missing.add(role)
                }
            }
            if (missing.isNotEmpty()) {
                error("Missing Geist fonts in assets/fonts: ${missing.joinToString { it.name }}")
            }
            initialized = true
        }
    }

    fun typeface(role: FaceRole): Typeface =
        byRole[role] ?: error("Geist fonts not loaded; call ensureLoaded first")
}
