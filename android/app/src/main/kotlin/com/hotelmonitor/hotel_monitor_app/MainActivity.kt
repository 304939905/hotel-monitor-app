package com.hotelmonitor.hotel_monitor_app

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "hotel_monitor/android_automation"
    private val captureRequestCode = 4601
    private val soundRequestCode = 4602
    private var soundPickerResult: MethodChannel.Result? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleMeituanTestIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleMeituanTestIntent(intent)
    }

    private fun handleMeituanTestIntent(intent: Intent) {
        val hotelName = intent.getStringExtra("automation_meituan_hotel").orEmpty()
        if (hotelName.isBlank()) return
        intent.removeExtra("automation_meituan_hotel")
        window.decorView.postDelayed({
            if (openMeituan()) HotelAccessibilityService.startMeituanHotel(hotelName)
        }, 500)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "isAccessibilityEnabled" -> result.success(isAccessibilityEnabled())
                    "isAccessibilityConnected" -> result.success(HotelAccessibilityService.isConnected())
                    "isAutomationRunning" -> result.success(
                        HotelAccessibilityService.isAutomationRunning(),
                    )
                    "getCaptchaPendingState" -> result.success(
                        HotelAccessibilityService.getCaptchaPendingState(),
                    )
                    "resumeCaptchaPending" -> {
                        val ready = HotelAccessibilityService.isConnected() &&
                            ScreenCaptureService.isReady()
                        result.success(ready && HotelAccessibilityService.resumeCaptchaPending())
                    }
                    "clearCaptchaPending" -> result.success(
                        HotelAccessibilityService.clearCaptchaPending(),
                    )
                    "isScreenCaptureReady" -> result.success(ScreenCaptureService.isReady())
                    "requestScreenCapture" -> {
                        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        startActivityForResult(manager.createScreenCaptureIntent(), captureRequestCode)
                        result.success(true)
                    }
                    "chooseCompletionSound" -> {
                        if (soundPickerResult != null) {
                            result.error("picker_busy", "音频选择器已打开", null)
                        } else {
                            soundPickerResult = result
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "audio/*"
                            }
                            startActivityForResult(intent, soundRequestCode)
                        }
                    }
                    "openAccessibilitySettings" -> {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        result.success(null)
                    }
                    "openCtrip" -> result.success(openCtrip())
                    "openCtripHotel" -> {
                        val opened = openCtrip()
                        val started = opened && HotelAccessibilityService.startCtripHotelNavigation()
                        result.success(started)
                    }
                    "prepareCtripForm" -> {
                        val city = call.argument<String>("city").orEmpty()
                        val hotelName = call.argument<String>("hotelName").orEmpty()
                        val ready = city.isNotBlank() && hotelName.isNotBlank() &&
                            HotelAccessibilityService.isConnected()
                        val started = ready &&
                            HotelAccessibilityService.startCtripForm(city, hotelName)
                        result.success(started)
                    }
                    "startCtripBatch" -> {
                        val rawTasks = call.argument<List<Map<String, String>>>("tasks").orEmpty()
                        val ready = rawTasks.isNotEmpty() && HotelAccessibilityService.isConnected()
                        result.success(ready && HotelAccessibilityService.startCtripBatch(rawTasks))
                    }
                    "getLastCtripRooms" -> result.success(
                        HotelAccessibilityService.getLastCtripRooms(),
                    )
                    "getLastCtripCapture" -> result.success(
                        HotelAccessibilityService.getLastCtripCapture(),
                    )
                    "getBatchCtripCaptures" -> result.success(
                        HotelAccessibilityService.getBatchCtripCaptures(),
                    )
                    "captureCurrentMeituanDetail" -> {
                        val hotelName = call.argument<String>("hotelName").orEmpty()
                        result.success(
                            HotelAccessibilityService.captureCurrentMeituanDetail(hotelName),
                        )
                    }
                    "startMeituanHotel" -> {
                        val hotelName = call.argument<String>("hotelName").orEmpty()
                        val ready = hotelName.isNotBlank() &&
                            HotelAccessibilityService.isConnected() &&
                            ScreenCaptureService.isReady()
                        result.success(ready && HotelAccessibilityService.startMeituanHotel(hotelName))
                    }
                    "startMeituanBatch" -> {
                        val tasks = call.argument<List<Map<String, String>>>("tasks").orEmpty()
                        val ready = tasks.isNotEmpty() &&
                            HotelAccessibilityService.isConnected() &&
                            ScreenCaptureService.isReady()
                        result.success(ready && HotelAccessibilityService.startMeituanBatch(tasks))
                    }
                    "startCombinedBatch" -> {
                        val ctripTasks =
                            call.argument<List<Map<String, String>>>("ctripTasks").orEmpty()
                        val meituanTasks =
                            call.argument<List<Map<String, String>>>("meituanTasks").orEmpty()
                        val ready = ctripTasks.isNotEmpty() && meituanTasks.isNotEmpty() &&
                            HotelAccessibilityService.isConnected() &&
                            ScreenCaptureService.isReady()
                        result.success(
                            ready && HotelAccessibilityService.startCombinedBatch(
                                ctripTasks,
                                meituanTasks,
                            ),
                        )
                    }
                    "getBatchMeituanCaptures" -> result.success(
                        HotelAccessibilityService.getBatchMeituanCaptures(),
                    )
                    "getLastMeituanCapture" -> result.success(
                        HotelAccessibilityService.getLastMeituanCapture(),
                    )
                    else -> result.notImplemented()
                }
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == soundRequestCode) {
            val pending = soundPickerResult
            soundPickerResult = null
            val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: Exception) {
                }
                getSharedPreferences("hotel_automation", MODE_PRIVATE).edit()
                    .putString("completion_sound_uri", uri.toString()).apply()
                pending?.success(uri.toString())
            } else {
                pending?.success(null)
            }
            return
        }
        if (requestCode != captureRequestCode || resultCode != Activity.RESULT_OK || data == null) return
        val service = Intent(this, ScreenCaptureService::class.java)
            .putExtra("resultCode", resultCode)
            .putExtra("resultData", data)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, HotelAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        for (service in splitter) {
            val component = ComponentName.unflattenFromString(service)
            if (component == expected) return true
        }
        return false
    }

    private fun openCtrip(): Boolean {
        val globalSearch = Intent().apply {
            setClassName(
                "ctrip.android.view",
                "ctrip.android.search.GlobalHomeSearchActivity",
            )
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        return try {
            startActivity(globalSearch)
            true
        } catch (_: Exception) {
            val fallback = packageManager.getLaunchIntentForPackage("ctrip.android.view")
                ?: return false
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(fallback)
            true
        }
    }

    private fun openMeituan(): Boolean {
        val intent = packageManager.getLaunchIntentForPackage("com.sankuai.meituan")
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        return true
    }

}
