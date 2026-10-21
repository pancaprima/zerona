package sh.zeron.zerona.core

import android.content.Context
import uniffi.zeron_core.FaceData
import uniffi.zeron_core.FaceRole

object FontLoader {
    private val assetFiles: List<Pair<FaceRole, String>> =
        listOf(
            FaceRole.SANS to "Geist.ttf",
            FaceRole.SANS_MEDIUM to "Geist-Medium.ttf",
            FaceRole.SANS_SEMIBOLD to "Geist-SemiBold.ttf",
            FaceRole.SANS_BOLD to "Geist-Bold.ttf",
            FaceRole.SANS_ITALIC to "Geist-Italic.ttf",
            FaceRole.SANS_MEDIUM_ITALIC to "Geist-MediumItalic.ttf",
            FaceRole.SANS_SEMIBOLD_ITALIC to "Geist-SemiBoldItalic.ttf",
            FaceRole.SANS_BOLD_ITALIC to "Geist-BoldItalic.ttf",
            FaceRole.MONO to "GeistMono.ttf",
            FaceRole.MONO_MEDIUM to "GeistMono-Medium.ttf",
            FaceRole.MONO_SEMIBOLD to "GeistMono-SemiBold.ttf",
            FaceRole.MONO_ITALIC to "GeistMono-Italic.ttf",
        )

    fun loadFaceData(context: Context): List<FaceData> {
        val assets = context.assets
        return assetFiles.mapNotNull { (role, name) ->
            runCatching {
                val bytes = assets.open("fonts/$name").use { it.readBytes() }
                FaceData(role, bytes)
            }.getOrNull()
        }
    }
}
