package com.hotelmonitor.hotel_monitor_app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.File
import java.io.FileOutputStream

class ScreenCaptureService : Service() {
    companion object {
        private const val CHANNEL_ID = "hotel_ocr_capture"
        private var instance: ScreenCaptureService? = null
        fun isReady() = instance?.projection != null
        fun recognize(callback: (List<Map<String, Any>>) -> Unit): Boolean {
            val service = instance ?: return false
            return service.captureText(callback)
        }

        fun captureScreenshot(prefix: String, callback: (String?) -> Unit): Boolean {
            val service = instance ?: return false
            return service.captureScreenshot(prefix, callback)
        }
    }

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("酒店截图采集已启用")
            .setContentText("仅在采集时读取并保存当前页面")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(4102, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (projection == null && intent != null) {
            val code = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            val data = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra("resultData", Intent::class.java)
            } else @Suppress("DEPRECATION") intent.getParcelableExtra("resultData")
            if (code == Activity.RESULT_OK && data != null) {
                val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projection = manager.getMediaProjection(code, data)
            }
        }
        return START_STICKY
    }

    private fun captureText(callback: (List<Map<String, Any>>) -> Unit): Boolean {
        return captureBitmap { cropped ->
            if (cropped == null) {
                callback(emptyList())
                return@captureBitmap
            }
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            recognizer.process(InputImage.fromBitmap(cropped, 0))
                .addOnSuccessListener { result ->
                    val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                        val box = line.boundingBox ?: return@mapNotNull null
                        mapOf<String, Any>("text" to line.text, "left" to box.left, "top" to box.top,
                            "right" to box.right, "bottom" to box.bottom)
                    }
                    callback(lines)
                }
                .addOnFailureListener { callback(emptyList()) }
                .addOnCompleteListener { cropped.recycle(); recognizer.close() }
        }
    }

    private fun captureScreenshot(prefix: String, callback: (String?) -> Unit): Boolean {
        return captureBitmap { bitmap ->
            if (bitmap == null) {
                callback(null)
                return@captureBitmap
            }
            val safePrefix = prefix.replace(Regex("[^A-Za-z0-9_-]"), "_").take(30)
            val directory = File(filesDir, "captures").apply { mkdirs() }
            val output = File(directory, "${safePrefix}_${System.currentTimeMillis()}.jpg")
            val saved = runCatching {
                FileOutputStream(output).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                }
            }.getOrDefault(false)
            bitmap.recycle()
            callback(output.absolutePath.takeIf { saved && output.exists() })
        }
    }

    private fun captureBitmap(callback: (Bitmap?) -> Unit): Boolean {
        val mediaProjection = projection ?: return false
        val metrics = resources.displayMetrics
        reader?.close()
        reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        val imageReader = reader!!
        val display = mediaProjection.createVirtualDisplay(
            "hotel_ocr", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.surface, null, null,
        )
        imageReader.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            val plane = image.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * image.width
            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / plane.pixelStride, image.height, Bitmap.Config.ARGB_8888,
            )
            bitmap.copyPixelsFromBuffer(plane.buffer)
            image.close()
            display?.release()
            imageReader.close()
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, metrics.widthPixels, metrics.heightPixels)
            bitmap.recycle()
            callback(cropped)
        }, mainLooper.let { android.os.Handler(it) })
        return true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "酒店截图采集", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onDestroy() { instance = null; reader?.close(); projection?.stop(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
