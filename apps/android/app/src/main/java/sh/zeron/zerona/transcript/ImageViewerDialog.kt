package sh.zeron.zerona.transcript

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Session-owned full-screen image preview. Constructed and held by [SessionImageLoader];
 * no static dialog state across sessions.
 */
internal class ImagePreviewController(
    private val activity: Activity,
    private val loader: SessionImageLoader,
    parentJob: Job,
    private val snapshot: ImageLoadSnapshot,
    private val onClosed: (ImagePreviewController) -> Unit,
) {
    private val dialogJob = SupervisorJob(parentJob)
    private val viewerScope = CoroutineScope(Dispatchers.Main.immediate + dialogJob)
    private var dialog: AlertDialog? = null
    private var previewImageView: ImageView? = null
    private var loadJob: Job? = null
    private var dismissed = false
    private var lifecycleObserver: DefaultLifecycleObserver? = null
    private var activityDestroyCallbacks: Application.ActivityLifecycleCallbacks? = null

    fun show() {
        if (dismissed || !activity.isUsableForUi()) return

        val metrics = activity.resources.displayMetrics
        val density = metrics.density
        val maxW = (metrics.widthPixels * 0.9f).toInt()
        val windowMaxH = (metrics.heightPixels * 0.7f).toInt()
        val chromePx = (56f * density).toInt()
        val contentH = (windowMaxH - chromePx).coerceAtLeast((120f * density).toInt())
        val pad = (16f * density).toInt()

        val root =
            FrameLayout(activity).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        maxW,
                        contentH,
                    )
                setBackgroundColor(NativeChrome.previewBackdrop(activity))
                setPadding(pad, pad, pad, pad)
            }
        val imageView =
            ImageView(activity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Image preview"
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER,
                    )
            }
        val progress =
            ProgressBar(activity).apply {
                isIndeterminate = true
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER,
                    )
            }
        val errorPanel =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                visibility = View.GONE
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER,
                    )
            }
        val errorText =
            TextView(activity).apply {
                text = "Couldn't load image"
                setTextColor(NativeChrome.previewOnSurface(activity))
                gravity = Gravity.CENTER
            }
        val retryButton =
            TextView(activity).apply {
                text = "Retry"
                setTextColor(NativeChrome.previewLink(activity))
                gravity = Gravity.CENTER
                setPadding(0, pad, 0, pad)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                isClickable = true
            }
        errorPanel.addView(errorText)
        errorPanel.addView(retryButton)
        root.addView(imageView)
        root.addView(progress)
        root.addView(errorPanel)
        previewImageView = imageView

        val builtDialog =
            NativeChrome.dialogBuilder(activity)
                .setTitle("Image")
                .setView(root)
                .setPositiveButton("Close", null)
                .create()

        builtDialog.setOnDismissListener {
            close()
        }

        retryButton.setOnClickListener {
            startLoad(forceReload = true, imageView, progress, errorPanel)
        }

        dialog = builtDialog
        registerDestroyHook()
        builtDialog.show()
        NativeChrome.applyDialogList(builtDialog)
        NativeChrome.applyDialogButtons(builtDialog)
        builtDialog.window?.setLayout(maxW, windowMaxH)
        startLoad(forceReload = false, imageView, progress, errorPanel)
    }

    fun close() {
        if (dismissed) return
        dismissed = true
        loadJob?.cancel()
        loadJob = null
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
        lifecycleObserver?.let { obs ->
            (activity as? LifecycleOwner)?.lifecycle?.removeObserver(obs)
        }
        lifecycleObserver = null
        activityDestroyCallbacks?.let { cb ->
            activity.application.unregisterActivityLifecycleCallbacks(cb)
        }
        activityDestroyCallbacks = null
        dialogJob.cancel()
        previewImageView?.setImageDrawable(null)
        previewImageView = null
        onClosed(this)
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

    private fun startLoad(
        forceReload: Boolean,
        imageView: ImageView,
        progress: ProgressBar,
        errorPanel: LinearLayout,
    ) {
        loadJob?.cancel()
        loadJob =
            viewerScope.launch {
                if (dismissed || !activity.isUsableForUi()) return@launch
                progress.visibility = View.VISIBLE
                errorPanel.visibility = View.GONE
                if (forceReload) {
                    imageView.setImageDrawable(null)
                }
                val requestGeneration = snapshot.generation
                val bitmap: Bitmap? =
                    try {
                        loader.loadBitmapForPreview(snapshot)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: OutOfMemoryError) {
                        null
                    } catch (_: Exception) {
                        null
                    }
                if (dismissed || !activity.isUsableForUi()) return@launch
                if (!loader.isPreviewStillValid(requestGeneration)) return@launch
                progress.visibility = View.GONE
                if (bitmap != null && !bitmap.isRecycled) {
                    errorPanel.visibility = View.GONE
                    imageView.setImageBitmap(bitmap)
                } else {
                    imageView.setImageDrawable(null)
                    errorPanel.visibility = View.VISIBLE
                }
            }
    }
}
