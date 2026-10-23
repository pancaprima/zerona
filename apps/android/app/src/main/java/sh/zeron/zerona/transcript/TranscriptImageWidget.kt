package sh.zeron.zerona.transcript

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.min

class TranscriptImageWidget(
    context: Context,
    val reference: String,
    private val actions: TranscriptActions,
) : FrameLayout(context) {
    var boundHostId: String? = null
    var boundReference: String? = null
    var boundGeneration: Long = 0L

    private val imageView =
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    private val progress =
        ProgressBar(context).apply {
            isIndeterminate = true
            visibility = GONE
        }
    private val errorLabel =
        TextView(context).apply {
            gravity = Gravity.CENTER
            text = "Couldn't load image"
            visibility = GONE
            textSize = 12f
            setTextColor(NativeChrome.onSurfaceVariant(context))
        }

    init {
        val cornerPx = (12f * resources.displayMetrics.density)
        clipToOutline = true
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(
                    view: View,
                    outline: Outline,
                ) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
                }
            }
        background =
            GradientDrawable().apply {
                setColor(NativeChrome.placeholderWash(NativeChrome.isDark(context)))
                cornerRadius = cornerPx
            }
        addView(imageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(progress, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(errorLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        contentDescription =
            if (ImageAttachmentSupport.isLabDemoReference(reference)) {
                "Lab demo image (local fixture)"
            } else {
                "Image attachment"
            }
        isClickable = true
        setOnClickListener { openPreview() }
        errorLabel.setOnClickListener {
            actions.imageLoader()?.loadInto(this)
        }
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        val r = min(w, h)
        val corner = if (r > 120 * resources.displayMetrics.density) 14f else 12f
        val cornerPx = corner * resources.displayMetrics.density
        (background as? GradientDrawable)?.cornerRadius = cornerPx
        invalidateOutline()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        actions.imageLoader()?.loadInto(this)
    }

    override fun onDetachedFromWindow() {
        actions.imageLoader()?.cancelLoad(this)
        imageView.setImageDrawable(null)
        super.onDetachedFromWindow()
    }

    fun clearForRebind() {
        progress.visibility = GONE
        errorLabel.visibility = GONE
        imageView.setImageDrawable(null)
        boundHostId = null
        boundReference = null
        boundGeneration = 0L
    }

    fun bindLoad(
        hostId: String,
        reference: String,
        generation: Long,
    ) {
        boundHostId = hostId
        boundReference = reference
        boundGeneration = generation
    }

    fun showLoading() {
        progress.visibility = VISIBLE
        errorLabel.visibility = GONE
        imageView.setImageDrawable(null)
    }

    fun showError() {
        progress.visibility = GONE
        errorLabel.visibility = VISIBLE
        errorLabel.text = "Couldn't load image\nTap to retry"
        errorLabel.contentDescription = "Image failed to load. Tap to retry."
        imageView.setImageDrawable(null)
    }

    fun showBitmap(bitmap: Bitmap) {
        progress.visibility = GONE
        errorLabel.visibility = GONE
        imageView.setImageBitmap(bitmap)
    }

    private fun openPreview() {
        val bmp =
            (imageView.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        actions.showImagePreview(context, reference, bmp)
    }
}
