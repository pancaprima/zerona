package sh.zeron.zerona.transcript

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import uniffi.zeron_core.CoreClient
import uniffi.zeron_core.maxAttachmentBytes
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal data class ImageLoadSnapshot(
    val host: String,
    val reference: String,
    val generation: Long,
    val core: CoreClient?,
    val maxReadBytes: Int,
    val labSession: Boolean,
)

class SessionImageLoader(
    parentScope: CoroutineScope,
) {
    private val scope = CoroutineScope(SupervisorJob(parentScope.coroutineContext[Job]) + Dispatchers.Main.immediate)
    private val ioSemaphore = Semaphore(3)
    private var boundCore: CoreClient? = null
    private var hostDeviceId: String = ""
    private var maxReadBytes: Int = ImageAttachmentSupport.MAX_READ_BYTES
    private var labSession: Boolean = false
    private val generation = AtomicLong(0)
    private val jobs = ConcurrentHashMap<TranscriptImageWidget, Job>()
    private var previewController: ImagePreviewController? = null

    private val bitmapCache =
        object : LruCache<String, Bitmap>(ImageAttachmentSupport.MAX_DECODED_BYTES) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.byteCount
        }

    /**
     * @return `true` when host, core, or lab identity changed (visible image widgets should reload).
     */
    fun bindSession(
        core: CoreClient?,
        hostId: String,
        labMode: Boolean = false,
    ): Boolean {
        val coreChanged = boundCore !== core
        val hostChanged = hostId != hostDeviceId
        val labChanged = labMode != labSession
        boundCore = core
        hostDeviceId = hostId
        labSession = labMode
        if (core != null && !labMode) {
            maxReadBytes =
                ImageAttachmentSupport.maxReadBytes(
                    runCatching { maxAttachmentBytes() }
                        .getOrDefault(ImageAttachmentSupport.DEFAULT_MAX_ATTACHMENT_BYTES),
                )
        } else if (labMode) {
            maxReadBytes = ImageAttachmentSupport.MAX_READ_BYTES
        }
        if (coreChanged || hostChanged || labChanged) {
            generation.incrementAndGet()
            cancelAllLoads()
            cancelPreview()
            bitmapCache.evictAll()
            return true
        }
        return false
    }

    fun close() {
        generation.incrementAndGet()
        cancelAllLoads()
        cancelPreview()
        bitmapCache.evictAll()
        scope.cancel()
        boundCore = null
        hostDeviceId = ""
        labSession = false
    }

    fun cancelLoad(widget: TranscriptImageWidget) {
        jobs.remove(widget)?.cancel()
    }

    fun loadInto(widget: TranscriptImageWidget) {
        val reference = widget.reference
        val snapshot = currentSnapshot(reference)
        jobs.remove(widget)?.cancel()
        widget.bindLoad(snapshot.host, reference, snapshot.generation)

        if (!ImageAttachmentSupport.isAllowedAttachmentReference(reference, allowLabFixture = snapshot.labSession)) {
            widget.showError()
            return
        }

        val cacheKey = ImageAttachmentSupport.cacheKey(snapshot.host, reference)
        val cached = bitmapCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            if (shouldApply(snapshot, widget)) {
                widget.showBitmap(cached)
            }
            return
        }

        widget.showLoading()
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val bitmap =
                        withContext(Dispatchers.IO) {
                            ioSemaphore.acquire()
                            try {
                                loadBitmap(snapshot)
                            } finally {
                                ioSemaphore.release()
                            }
                        }
                    if (!shouldApply(snapshot, widget)) {
                        bitmap?.recycle()
                        return@launch
                    }
                    if (bitmap == null) {
                        widget.showError()
                    } else {
                        bitmapCache.put(cacheKey, bitmap)
                        widget.showBitmap(bitmap)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: OutOfMemoryError) {
                    if (shouldApply(snapshot, widget)) {
                        widget.showError()
                    }
                } catch (_: Exception) {
                    if (shouldApply(snapshot, widget)) {
                        widget.showError()
                    }
                } finally {
                    jobs.remove(widget, coroutineContext[Job])
                }
            }
        jobs[widget] = job
        job.start()
    }

    fun showPreview(
        context: Context,
        reference: String,
        thumbnail: Bitmap?,
    ) {
        if (!ImageAttachmentSupport.isAllowedAttachmentReference(reference, allowLabFixture = labSession)) {
            return
        }
        val activity = context.findActivity()?.takeIf { it.isUsableForUi() } ?: return
        val snapshot = currentSnapshot(reference)
        cancelPreview()
        val controller =
            ImagePreviewController(
                activity = activity,
                loader = this,
                parentJob = scope.coroutineContext[Job] ?: return,
                snapshot = snapshot,
                onClosed = { closed ->
                    if (previewController === closed) {
                        previewController = null
                    }
                },
            )
        previewController = controller
        controller.show()
    }

    internal fun isPreviewStillValid(requestGeneration: Long): Boolean =
        generation.get() == requestGeneration

    internal suspend fun loadBitmapForPreview(snapshot: ImageLoadSnapshot): Bitmap? {
        val requestGeneration = snapshot.generation
        if (!ImageAttachmentSupport.isAllowedAttachmentReference(snapshot.reference, allowLabFixture = snapshot.labSession)) {
            return null
        }
        val cacheKey = ImageAttachmentSupport.cacheKey(snapshot.host, snapshot.reference)
        val cached = bitmapCache.get(cacheKey)
        if (cached != null && !cached.isRecycled && generation.get() == requestGeneration) {
            return cached
        }
        return withContext(Dispatchers.IO) {
            ioSemaphore.acquire()
            try {
                val bitmap = loadBitmap(snapshot)
                if (snapshot.generation != generation.get()) {
                    bitmap?.recycle()
                    return@withContext null
                }
                bitmap
            } finally {
                ioSemaphore.release()
            }
        }
    }

    private fun currentSnapshot(reference: String): ImageLoadSnapshot =
        ImageLoadSnapshot(
            host = hostDeviceId,
            reference = reference,
            generation = generation.get(),
            core = boundCore,
            maxReadBytes = maxReadBytes,
            labSession = labSession,
        )

    private fun shouldApply(
        snapshot: ImageLoadSnapshot,
        widget: TranscriptImageWidget,
    ): Boolean =
        ImageAttachmentSupport.shouldApplyLoad(
            snapshot.host,
            snapshot.reference,
            snapshot.generation,
            generation.get(),
            widget.boundHostId,
            widget.boundReference,
            widget.boundGeneration,
        )

    private suspend fun loadBitmap(snapshot: ImageLoadSnapshot): Bitmap? {
        if (snapshot.generation != generation.get()) return null
        val reference = snapshot.reference
        if (!ImageAttachmentSupport.isAllowedAttachmentReference(reference, allowLabFixture = snapshot.labSession)) {
            return null
        }
        if (snapshot.labSession && ImageAttachmentSupport.isLabDemoReference(reference)) {
            return labDemoBitmap()
        }
        val core = snapshot.core ?: return null
        if (snapshot.host.isBlank()) return null
        if (snapshot.maxReadBytes <= 0) return null
        val bytes =
            try {
                core.readAttachment(snapshot.host, reference)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return null
            }
        if (snapshot.generation != generation.get()) return null
        if (bytes.size > snapshot.maxReadBytes) return null
        val bitmap = decodeBounded(bytes)
        if (snapshot.generation != generation.get()) {
            bitmap?.recycle()
            return null
        }
        return bitmap
    }

    private fun decodeBounded(bytes: ByteArray): Bitmap? {
        if (ImageAttachmentSupport.sniffImageMime(bytes) == null) return null
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        val bounds =
            ImageAttachmentSupport.DecodeBounds(
                width = boundsOpts.outWidth,
                height = boundsOpts.outHeight,
            )
        val plan = ImageAttachmentSupport.planSampledDecode(bounds) ?: return null
        val decodeOpts =
            BitmapFactory.Options().apply {
                inSampleSize = plan.inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
        val bitmap =
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            } catch (_: OutOfMemoryError) {
                return null
            }
        if (bitmap == null) return null
        val bytesUsed = bitmap.allocationByteCount
        if (bytesUsed > ImageAttachmentSupport.MAX_DECODED_BYTES) {
            bitmap.recycle()
            return null
        }
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            bitmap.recycle()
            return null
        }
        val estimated = ImageAttachmentSupport.bitmapByteSize(bitmap.width, bitmap.height)
        if (estimated > ImageAttachmentSupport.MAX_DECODED_BYTES) {
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    private fun labDemoBitmap(): Bitmap {
        val w = 320
        val h = 200
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    LinearGradient(
                        0f,
                        0f,
                        w.toFloat(),
                        h.toFloat(),
                        intArrayOf(0xFF5C6BC0.toInt(), 0xFF26A69A.toInt()),
                        null,
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        val label =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 36f
                textAlign = Paint.Align.CENTER
            }
        canvas.drawText("Lab demo image", w / 2f, h / 2f + 12f, label)
        canvas.drawText("(not from host)", w / 2f, h / 2f + 48f, label.apply { textSize = 24f })
        return bmp
    }

    private fun cancelAllLoads() {
        for ((widget, job) in jobs) {
            job.cancel()
            jobs.remove(widget, job)
        }
    }

    private fun cancelPreview() {
        val controller = previewController
        if (controller == null) return
        previewController = null
        controller.close()
    }
}
