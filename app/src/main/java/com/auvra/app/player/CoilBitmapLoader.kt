package com.auvra.app.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.graphics.createBitmap
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope
) : BitmapLoader {

    private val executor = Executors.newSingleThreadExecutor()

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    private fun createFallbackBitmap(): Bitmap = createBitmap(64, 64)

    private fun Bitmap.createIndependentCopy(): Bitmap {
        if (isRecycled) return createFallbackBitmap()
        return try {
            val copy = createBitmap(width, height)
            val canvas = Canvas(copy)
            canvas.drawBitmap(this, 0f, 0f, null)
            copy
        } catch (e: Exception) {
            createFallbackBitmap()
        }
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        executor.execute {
            try {
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                future.set(bitmap?.createIndependentCopy() ?: createFallbackBitmap())
            } catch (e: Exception) {
                future.set(createFallbackBitmap())
            }
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false)
                    .build()

                when (val result = context.imageLoader.execute(request)) {
                    is SuccessResult -> {
                        val bitmap = result.image.toBitmap()
                        future.set(bitmap.createIndependentCopy())
                    }
                    else -> {
                        future.set(createFallbackBitmap())
                    }
                }
            } catch (e: Exception) {
                future.set(createFallbackBitmap())
            }
        }
        return future
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        metadata.artworkData?.let { return decodeBitmap(it) }
        val artworkUri = metadata.artworkUri ?: return null
        return loadBitmap(artworkUri)
    }
}
