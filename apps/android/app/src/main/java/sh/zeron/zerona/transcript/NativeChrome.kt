package sh.zeron.zerona.transcript

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import sh.zeron.zerona.R
import sh.zeron.zerona.ui.theme.ZeronaColorTokens

/** Theme-aware colours and dialog wiring for native View/AlertDialog chrome. */
internal object NativeChrome {
    fun isDark(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    fun onSurface(context: Context): Int = ZeronaColorTokens.onSurfaceArgb(isDark(context))

    fun onSurfaceVariant(context: Context): Int = ZeronaColorTokens.onSurfaceVariantArgb(isDark(context))

    fun surface(context: Context): Int = ZeronaColorTokens.surfaceArgb(isDark(context))

    fun error(context: Context): Int = ZeronaColorTokens.errorArgb(isDark(context))

    fun primary(context: Context): Int = ZeronaColorTokens.primaryArgb(isDark(context))

    fun previewBackdrop(context: Context): Int = ZeronaColorTokens.previewBackdropArgb(isDark(context))

    fun linkAccent(context: Context): Int = ZeronaColorTokens.linkAccentArgb(isDark(context))

    fun previewOnSurface(context: Context): Int =
        ZeronaColorTokens.previewOnSurfaceArgb(isDark(context))

    fun previewLink(context: Context): Int = ZeronaColorTokens.previewLinkArgb(isDark(context))

    fun dialogBuilder(context: Context): AlertDialog.Builder {
        val theme =
            if (isDark(context)) {
                R.style.Theme_Zerona_Dialog_Dark
            } else {
                R.style.Theme_Zerona_Dialog
            }
        return AlertDialog.Builder(context, theme)
    }

    fun applyReadableText(
        view: TextView,
        context: Context,
    ) {
        view.setTextColor(onSurface(context))
    }

    fun applyFormControl(
        view: TextView,
        context: Context,
    ) {
        view.setTextColor(onSurface(context))
    }

    fun applyCheckBox(
        box: CheckBox,
        context: Context,
    ) {
        box.setTextColor(onSurface(context))
    }

    fun applyDialogList(dialog: AlertDialog) {
        val context = dialog.context
        dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(onSurface(context))
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(onSurfaceVariant(context))
        dialog.listView?.let { list -> themeListView(list, context) }
    }

    fun applyDialogButtons(dialog: AlertDialog) {
        val context = dialog.context
        val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        positive?.setTextColor(primary(context))
        negative?.setTextColor(onSurfaceVariant(context))
        neutral?.setTextColor(onSurfaceVariant(context))
    }

    fun themeListView(
        list: ListView,
        context: Context,
    ) {
        list.divider?.mutate()?.setTint(ZeronaColorTokens.outlineArgb(isDark(context)))
        list.setBackgroundColor(surface(context))
    }

    fun themeButton(
        button: Button,
        context: Context,
    ) {
        button.setTextColor(primary(context))
    }

    fun placeholderWash(dark: Boolean): Int =
        if (dark) {
            Color.argb(0x33, 0xFF, 0xFF, 0xFF)
        } else {
            Color.argb(0x22, 0x1C, 0x1B, 0x22)
        }
}
