package sh.zeron.zerona.transcript

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.TransactionTooLargeException
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Activity-scoped message action menu and native selectable text panel. Owned by [SessionViewModel].
 */
internal class MessageActionsController(
    private val activity: Activity,
    private val menuState: MessageActionMenuState,
    private val onClosed: (MessageActionsController) -> Unit,
) {
    private sealed class MenuAction {
        data object CopyMessage : MenuAction()

        data object SelectText : MenuAction()

        data class CopyLinkDirect(val url: String) : MenuAction()

        data object CopyLinkChooser : MenuAction()

        data object ViewSource : MenuAction()
    }

    private var menuDialog: AlertDialog? = null
    private var selectionDialog: AlertDialog? = null
    private var linkChooserDialog: AlertDialog? = null
    private var dismissed = false
    private var deferControllerRelease = false
    private var lifecycleObserver: DefaultLifecycleObserver? = null
    private var activityDestroyCallbacks: Application.ActivityLifecycleCallbacks? = null

    fun show() {
        if (dismissed || !activity.isUsableForUi()) {
            close()
            return
        }
        registerDestroyHook()
        showMenu()
    }

    fun close() {
        if (dismissed) return
        dismissed = true
        dismissMenuDialog(releaseController = false)
        dismissSelectionDialog(releaseController = false)
        dismissLinkChooserDialog(releaseController = false)
        lifecycleObserver?.let { obs ->
            (activity as? LifecycleOwner)?.lifecycle?.removeObserver(obs)
        }
        lifecycleObserver = null
        activityDestroyCallbacks?.let { cb ->
            activity.application.unregisterActivityLifecycleCallbacks(cb)
        }
        activityDestroyCallbacks = null
        onClosed(this)
    }

    private fun releaseIfIdle() {
        if (dismissed) return
        if (menuDialog != null || selectionDialog != null || linkChooserDialog != null) return
        close()
    }

    private fun dismissMenuDialog(releaseController: Boolean) {
        val dialog = menuDialog
        if (dialog == null) {
            if (releaseController) releaseIfIdle()
            return
        }
        menuDialog = null
        dialog.setOnDismissListener(null)
        dialog.dismiss()
        if (releaseController) releaseIfIdle()
    }

    private fun dismissSelectionDialog(releaseController: Boolean) {
        val dialog = selectionDialog
        if (dialog == null) {
            if (releaseController) releaseIfIdle()
            return
        }
        selectionDialog = null
        dialog.setOnDismissListener(null)
        dialog.dismiss()
        if (releaseController) releaseIfIdle()
    }

    private fun dismissLinkChooserDialog(releaseController: Boolean) {
        val dialog = linkChooserDialog
        if (dialog == null) {
            if (releaseController) releaseIfIdle()
            return
        }
        linkChooserDialog = null
        dialog.setOnDismissListener(null)
        dialog.dismiss()
        if (releaseController) releaseIfIdle()
    }

    private fun showMenu() {
        val actions = ArrayList<MenuAction>()
        val labels = ArrayList<String>()
        if (menuState.copyMessageEnabled) {
            labels.add("Copy message")
            actions.add(MenuAction.CopyMessage)
        }
        if (menuState.selectTextEnabled) {
            labels.add("Select text")
            actions.add(MenuAction.SelectText)
        }
        if (menuState.copyLinkEnabled) {
            labels.add("Copy link")
            val direct = menuState.resolvedCopyLinkUrl
            if (direct != null) {
                actions.add(MenuAction.CopyLinkDirect(direct))
            } else {
                actions.add(MenuAction.CopyLinkChooser)
            }
        }
        if (menuState.viewSourceEnabled) {
            labels.add("View source")
            actions.add(MenuAction.ViewSource)
        }
        if (labels.isEmpty()) {
            close()
            return
        }

        val menuBuilt =
            NativeChrome.dialogBuilder(activity)
                .setTitle("Message")
                .setItems(labels.toTypedArray()) { _, which ->
                    when (val action = actions.getOrNull(which)) {
                        null -> {
                            dismissMenuDialog(releaseController = true)
                        }
                        MenuAction.CopyMessage -> {
                            writeClipboard(menuState.clipboard)
                            dismissMenuDialog(releaseController = true)
                        }
                        MenuAction.SelectText -> {
                            deferControllerRelease = true
                            dismissMenuDialog(releaseController = false)
                            deferControllerRelease = false
                            showSelectionPanel(showSource = false)
                        }
                        is MenuAction.CopyLinkDirect -> {
                            writeClipboardLink(action.url)
                            dismissMenuDialog(releaseController = true)
                        }
                        MenuAction.CopyLinkChooser -> {
                            deferControllerRelease = true
                            dismissMenuDialog(releaseController = false)
                            deferControllerRelease = false
                            showLinkChooser(menuState.links)
                        }
                        MenuAction.ViewSource -> {
                            deferControllerRelease = true
                            dismissMenuDialog(releaseController = false)
                            deferControllerRelease = false
                            showSelectionPanel(showSource = true)
                        }
                    }
                }
                .setOnCancelListener {
                    dismissMenuDialog(releaseController = true)
                }
                .create()
        menuDialog = menuBuilt
        menuBuilt.setOnDismissListener {
            menuDialog = null
            if (!deferControllerRelease) {
                releaseIfIdle()
            }
        }
        menuBuilt.show()
        NativeChrome.applyDialogList(menuBuilt)
        menuBuilt.listView?.let { list ->
            val minPx = (48f * activity.resources.displayMetrics.density).toInt()
            for (i in 0 until list.childCount) {
                val row = list.getChildAt(i)
                row.minimumHeight = minPx
            }
        }
    }

    private fun showLinkChooser(links: List<MessageLinkDescriptor>) {
        if (!activity.isUsableForUi()) {
            close()
            return
        }
        if (links.isEmpty()) {
            releaseIfIdle()
            return
        }
        val labels = links.map { it.label }.toTypedArray()
        val built =
            NativeChrome.dialogBuilder(activity)
                .setTitle("Copy link")
                .setItems(labels) { _, which ->
                    links.getOrNull(which)?.let { writeClipboardLink(it.url) }
                    dismissLinkChooserDialog(releaseController = true)
                }
                .setOnCancelListener {
                    dismissLinkChooserDialog(releaseController = true)
                }
                .create()
        linkChooserDialog = built
        built.setOnDismissListener {
            linkChooserDialog = null
            releaseIfIdle()
        }
        built.show()
        NativeChrome.applyDialogList(built)
    }

    private fun showSelectionPanel(showSource: Boolean) {
        if (!activity.isUsableForUi()) {
            close()
            return
        }
        val displayText = menuState.selectionDisplayText
        if (displayText.isEmpty()) {
            Toast.makeText(activity, "No text to show", Toast.LENGTH_SHORT).show()
            releaseIfIdle()
            return
        }

        val density = activity.resources.displayMetrics.density
        val pad = (16f * density).toInt()
        val metrics = activity.resources.displayMetrics
        val maxDialogH = (metrics.heightPixels * 0.70f).toInt()

        val root =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        maxDialogH,
                    )
            }

        val monoToggle =
            CheckBox(activity).apply {
                text = if (showSource) "Monospace (source)" else "Monospace"
                isChecked = showSource
                visibility =
                    if (showSource || menuState.viewSourceEnabled) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                minimumHeight = (48f * density).toInt()
                NativeChrome.applyCheckBox(this, activity)
            }

        val bodyView =
            TextView(activity).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                setTextIsSelectable(true)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(0, pad / 2, 0, pad)
                text = displayText
                if (showSource) {
                    typeface = Typeface.MONOSPACE
                }
                NativeChrome.applyReadableText(this, activity)
            }

        monoToggle.setOnCheckedChangeListener { _, checked ->
            bodyView.typeface = if (checked) Typeface.MONOSPACE else Typeface.DEFAULT
        }

        val scroll =
            NestedScrollView(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f,
                    )
                isFillViewport = true
                addView(
                    bodyView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }

        val buttonRow =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
        val copyAll =
            Button(activity).apply {
                text = "Copy all"
                setOnClickListener {
                    writeClipboard(TranscriptMessageActionsSupport.prepareClipboardText(menuState.selectionText))
                }
                NativeChrome.themeButton(this, activity)
            }
        val closeBtn =
            Button(activity).apply {
                text = "Close"
                setOnClickListener {
                    dismissSelectionDialog(releaseController = true)
                }
                NativeChrome.themeButton(this, activity)
            }
        buttonRow.addView(copyAll)
        buttonRow.addView(closeBtn)

        root.addView(monoToggle)
        root.addView(scroll)
        root.addView(buttonRow)

        val title = if (showSource) "Source text" else "Message text"
        val built =
            NativeChrome.dialogBuilder(activity)
                .setTitle(title)
                .setView(root)
                .create()
        selectionDialog = built
        built.setOnDismissListener {
            selectionDialog = null
            releaseIfIdle()
        }
        built.show()
        built.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            maxDialogH,
        )
        bodyView.post {
            if (bodyView.isAttachedToWindow) {
                bodyView.requestFocus()
            }
        }
    }

    private fun writeClipboard(clipboard: ClipboardText) {
        if (clipboard.text.isEmpty()) {
            Toast.makeText(activity, "Nothing to copy", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("transcript", clipboard.text))
            if (clipboard.truncated) {
                Toast.makeText(activity, "Copied with truncation notice", Toast.LENGTH_SHORT).show()
            }
        } catch (_: SecurityException) {
            Toast.makeText(activity, "Clipboard unavailable", Toast.LENGTH_SHORT).show()
        } catch (_: TransactionTooLargeException) {
            Toast.makeText(activity, "Text too large to copy", Toast.LENGTH_SHORT).show()
        } catch (_: RuntimeException) {
            Toast.makeText(activity, "Clipboard unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeClipboardLink(url: String) {
        when (val plan = TranscriptMessageActionsSupport.prepareLinkClipboard(url)) {
            ClipboardWritePlan.LinkTooLarge ->
                Toast.makeText(activity, "Link too large to copy", Toast.LENGTH_SHORT).show()
            is ClipboardWritePlan.Ready -> writeClipboard(plan.clipboard)
        }
    }

    private fun registerDestroyHook() {
        val owner = activity as? LifecycleOwner
        if (owner != null) {
            val observer =
                object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        close()
                    }
                }
            lifecycleObserver = observer
            owner.lifecycle.addObserver(observer)
            return
        }
        val callbacks =
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityDestroyed(a: Activity) {
                    if (a === activity) {
                        activity.application.unregisterActivityLifecycleCallbacks(this)
                        activityDestroyCallbacks = null
                        close()
                    }
                }

                override fun onActivityCreated(
                    a: Activity,
                    savedInstanceState: android.os.Bundle?,
                ) {
                }

                override fun onActivityStarted(a: Activity) {}

                override fun onActivityResumed(a: Activity) {}

                override fun onActivityPaused(a: Activity) {}

                override fun onActivityStopped(a: Activity) {}

                override fun onActivitySaveInstanceState(
                    a: Activity,
                    outState: android.os.Bundle,
                ) {
                }
            }
        activityDestroyCallbacks = callbacks
        activity.application.registerActivityLifecycleCallbacks(callbacks)
    }
}
