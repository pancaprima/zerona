package sh.zeron.zerona.ui.theme

/**
 * Calm v1 lilac / peach / mint palette (ARGB). Shared by Compose [Theme], JVM tests,
 * and native transcript chrome via [sh.zeron.zerona.transcript.NativeChrome].
 */
object ZeronaColorTokens {
    // Light — from zerona-android-v1-catalog CSS variables
    const val LIGHT_BACKGROUND = 0xFFEBE8F0.toInt()
    const val LIGHT_SURFACE = 0xFFF7F5FA.toInt()
    const val LIGHT_SURFACE_VARIANT = 0xFFEEEAF4.toInt()
    const val LIGHT_SURFACE_CONTAINER = 0xFFEEEAF4.toInt()
    const val LIGHT_SURFACE_CONTAINER_HIGH = 0xFFE8DFF8.toInt()
    const val LIGHT_ON_SURFACE = 0xFF1C1B22.toInt()
    const val LIGHT_ON_SURFACE_VARIANT = 0xFF5C5768.toInt()
    const val LIGHT_OUTLINE = 0xFFC9C2D4.toInt()
    const val LIGHT_OUTLINE_VARIANT = 0xFFD8D2E0.toInt()
    /** Action / link lilac (--primary-action, matches `zerona_primary` XML). */
    const val LIGHT_PRIMARY = 0xFF5C45A8.toInt()
    /** Decorative catalog lilac (quote bars, non-text accents). */
    const val LIGHT_DECORATIVE_LILAC = 0xFF7B5FD4.toInt()
    const val LIGHT_ON_PRIMARY = 0xFFFFFFFF.toInt()
    const val LIGHT_PRIMARY_CONTAINER = 0xFFE8DFF8.toInt()
    const val LIGHT_ON_PRIMARY_CONTAINER = 0xFF2D1F52.toInt()
    const val LIGHT_SECONDARY = 0xFFE89A72.toInt()
    const val LIGHT_ON_SECONDARY = 0xFF1C1B22.toInt()
    const val LIGHT_SECONDARY_CONTAINER = 0xFFFCE8DC.toInt()
    const val LIGHT_ON_SECONDARY_CONTAINER = 0xFF3D2818.toInt()
    const val LIGHT_TERTIARY = 0xFF4DB89A.toInt()
    const val LIGHT_ON_TERTIARY = 0xFF0F3D32.toInt()
    const val LIGHT_TERTIARY_CONTAINER = 0xFFD4F0E8.toInt()
    const val LIGHT_ON_TERTIARY_CONTAINER = 0xFF0F3D32.toInt()
    const val LIGHT_ERROR = 0xFFB3261E.toInt()
    const val LIGHT_ON_ERROR = 0xFFFFFFFF.toInt()
    const val LIGHT_ERROR_CONTAINER = 0xFFFDE8E6.toInt()
    const val LIGHT_ON_ERROR_CONTAINER = 0xFF410E0B.toInt()
    const val LIGHT_SUCCESS = 0xFF126B34.toInt()
    const val LIGHT_ON_SUCCESS = 0xFFFFFFFF.toInt()

    // Dark
    const val DARK_BACKGROUND = 0xFF141218.toInt()
    const val DARK_SURFACE = 0xFF1C1B22.toInt()
    const val DARK_SURFACE_VARIANT = 0xFF26242E.toInt()
    const val DARK_SURFACE_CONTAINER = 0xFF26242E.toInt()
    const val DARK_SURFACE_CONTAINER_HIGH = 0xFF312F3A.toInt()
    const val DARK_ON_SURFACE = 0xFFECE8F4.toInt()
    const val DARK_ON_SURFACE_VARIANT = 0xFFA8A2B4.toInt()
    const val DARK_OUTLINE = 0xFF48445A.toInt()
    const val DARK_OUTLINE_VARIANT = 0xFF3A3748.toInt()
    const val DARK_PRIMARY = 0xFFB8A0F0.toInt()
    const val DARK_ON_PRIMARY = 0xFF1A1228.toInt()
    const val DARK_PRIMARY_CONTAINER = 0xFF3D2F62.toInt()
    const val DARK_ON_PRIMARY_CONTAINER = 0xFFECE8F4.toInt()
    const val DARK_SECONDARY = 0xFFE89A72.toInt()
    const val DARK_ON_SECONDARY = 0xFF1A1228.toInt()
    const val DARK_SECONDARY_CONTAINER = 0xFF4A3020.toInt()
    const val DARK_ON_SECONDARY_CONTAINER = 0xFFFCE8DC.toInt()
    const val DARK_TERTIARY = 0xFF4DB89A.toInt()
    const val DARK_ON_TERTIARY = 0xFF0F3D32.toInt()
    const val DARK_TERTIARY_CONTAINER = 0xFF1A4D40.toInt()
    const val DARK_ON_TERTIARY_CONTAINER = 0xFFD4F0E8.toInt()
    const val DARK_ERROR = 0xFFF2B8B5.toInt()
    const val DARK_ON_ERROR = 0xFF601410.toInt()
    const val DARK_ERROR_CONTAINER = 0xFF3D2020.toInt()
    const val DARK_ON_ERROR_CONTAINER = 0xFFF2B8B5.toInt()
    const val DARK_SUCCESS = 0xFF34D399.toInt()
    const val DARK_ON_SUCCESS = 0xFF052E1C.toInt()

    const val LIGHT_COMPOSER_DOCK = LIGHT_SURFACE_CONTAINER
    const val DARK_COMPOSER_DOCK = DARK_SURFACE_CONTAINER

    const val LIGHT_PREVIEW_BACKDROP = 0xFFF7F5FA.toInt()
    const val DARK_PREVIEW_BACKDROP = 0xFF0E0D12.toInt()
    const val LIGHT_PREVIEW_ON_SURFACE = 0xFF1C1B22.toInt()
    const val DARK_PREVIEW_ON_SURFACE = 0xFFECE8F4.toInt()
    const val LIGHT_PREVIEW_LINK = LIGHT_PRIMARY
    const val DARK_PREVIEW_LINK = DARK_PRIMARY

    fun backgroundArgb(dark: Boolean): Int = if (dark) DARK_BACKGROUND else LIGHT_BACKGROUND

    fun surfaceArgb(dark: Boolean): Int = if (dark) DARK_SURFACE else LIGHT_SURFACE

    fun onSurfaceArgb(dark: Boolean): Int = if (dark) DARK_ON_SURFACE else LIGHT_ON_SURFACE

    fun onSurfaceVariantArgb(dark: Boolean): Int = if (dark) DARK_ON_SURFACE_VARIANT else LIGHT_ON_SURFACE_VARIANT

    fun primaryArgb(dark: Boolean): Int = if (dark) DARK_PRIMARY else LIGHT_PRIMARY

    fun onPrimaryArgb(dark: Boolean): Int = if (dark) DARK_ON_PRIMARY else LIGHT_ON_PRIMARY

    fun composerDockArgb(dark: Boolean): Int = if (dark) DARK_COMPOSER_DOCK else LIGHT_COMPOSER_DOCK

    fun outlineArgb(dark: Boolean): Int = if (dark) DARK_OUTLINE else LIGHT_OUTLINE

    fun errorArgb(dark: Boolean): Int = if (dark) DARK_ERROR else LIGHT_ERROR

    fun previewBackdropArgb(dark: Boolean): Int =
        if (dark) DARK_PREVIEW_BACKDROP else LIGHT_PREVIEW_BACKDROP

    fun previewOnSurfaceArgb(dark: Boolean): Int =
        if (dark) DARK_PREVIEW_ON_SURFACE else LIGHT_PREVIEW_ON_SURFACE

    fun previewLinkArgb(dark: Boolean): Int =
        if (dark) DARK_PREVIEW_LINK else LIGHT_PREVIEW_LINK

    fun linkAccentArgb(dark: Boolean): Int = primaryArgb(dark)
}
