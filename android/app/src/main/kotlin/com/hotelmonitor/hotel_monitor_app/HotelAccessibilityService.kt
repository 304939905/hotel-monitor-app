package com.hotelmonitor.hotel_monitor_app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Rect
import android.graphics.Path
import android.content.ClipData
import android.content.ClipboardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.media.RingtoneManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

class HotelAccessibilityService : AccessibilityService() {
    companion object {
        private const val PREFS_NAME = "hotel_automation"
        private const val LAST_CTRIP_ROOMS = "last_ctrip_rooms"
        private const val LAST_CTRIP_CAPTURE = "last_ctrip_capture"
        private const val BATCH_CTRIP_CAPTURES = "batch_ctrip_captures"
        private const val LAST_MEITUAN_CAPTURE = "last_meituan_capture"
        private const val BATCH_MEITUAN_CAPTURES = "batch_meituan_captures"
        private const val COMPLETION_SOUND_URI = "completion_sound_uri"
        private const val CAPTCHA_PENDING_STATE = "captcha_pending_state"
        private var instance: HotelAccessibilityService? = null

        fun startCtripHotelNavigation(): Boolean {
            val service = instance ?: return false
            service.startHotelOnlyNavigation()
            return true
        }

        fun startCtripForm(city: String, hotelName: String): Boolean {
            val service = instance ?: return false
            service.startCtripFormFromLauncher(city, hotelName)
            return true
        }

        fun startCtripBatch(tasks: List<Map<String, String>>): Boolean {
            val service = instance ?: return false
            if (tasks.isEmpty()) return false
            service.startStandaloneCtripBatch(tasks)
            return true
        }

        fun getLastCtripRooms(): List<Map<String, Any>> {
            val service = instance ?: return emptyList()
            val raw = service.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(LAST_CTRIP_ROOMS, "[]") ?: "[]"
            val result = mutableListOf<Map<String, Any>>()
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                result += mapOf(
                    "name" to item.optString("name"),
                    "price" to item.optInt("price"),
                )
            }
            return result
        }

        fun getLastCtripCapture(): Map<String, Any> {
            val service = instance ?: return emptyMap()
            val raw = service.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(LAST_CTRIP_CAPTURE, null) ?: return emptyMap()
            val item = JSONObject(raw)
            return mutableMapOf<String, Any>(
                "taskId" to item.optString("taskId"),
                "hotelName" to item.optString("hotelName"),
                "screenshotPath" to item.optString("screenshotPath"),
                "capturedAt" to item.optLong("capturedAt"),
                "status" to item.optString("status", if (item.has("price")) "success" else "error"),
            ).apply {
                if (item.has("price")) put("price", item.optInt("price"))
            }
        }

        fun getBatchCtripCaptures(): List<Map<String, Any>> {
            val service = instance ?: return emptyList()
            val raw = service.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(BATCH_CTRIP_CAPTURES, "[]") ?: "[]"
            val array = JSONArray(raw)
            return (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                mutableMapOf<String, Any>(
                    "taskId" to item.optString("taskId"),
                    "hotelName" to item.optString("hotelName"),
                    "screenshotPath" to item.optString("screenshotPath"),
                    "capturedAt" to item.optLong("capturedAt"),
                    "status" to item.optString("status", if (item.has("price")) "success" else "error"),
                ).apply {
                    if (item.has("price")) put("price", item.optInt("price"))
                }
            }
        }

        fun captureCurrentMeituanDetail(hotelName: String): Map<String, Any> {
            val service = instance ?: return emptyMap()
            return service.captureMeituanDetail(hotelName)
        }

        fun startMeituanHotel(hotelName: String): Boolean {
            val service = instance ?: return false
            service.startMeituanHotelFromLauncher(hotelName)
            return true
        }

        fun startMeituanBatch(tasks: List<Map<String, String>>): Boolean {
            val service = instance ?: return false
            if (tasks.isEmpty()) return false
            service.combinedRunActive = false
            service.pendingCombinedMeituanTasks = emptyList()
            service.startMeituanBatchFromLauncher(tasks)
            return true
        }

        fun startCombinedBatch(
            ctripTasks: List<Map<String, String>>,
            meituanTasks: List<Map<String, String>>,
        ): Boolean {
            val service = instance ?: return false
            if (ctripTasks.isEmpty() || meituanTasks.isEmpty()) return false
            service.startCombinedBatchQueues(ctripTasks, meituanTasks)
            return true
        }

        fun getBatchMeituanCaptures(): List<Map<String, Any>> {
            val service = instance ?: return emptyList()
            val raw = service.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(BATCH_MEITUAN_CAPTURES, "[]") ?: "[]"
            val array = JSONArray(raw)
            return (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                mutableMapOf<String, Any>(
                    "taskId" to item.optString("taskId"),
                    "hotelName" to item.optString("hotelName"),
                    "screenshotPath" to item.optString("screenshotPath"),
                    "capturedAt" to item.optLong("capturedAt"),
                    "status" to item.optString("status", if (item.has("price")) "success" else "error"),
                ).apply {
                    if (item.has("price")) put("price", item.optInt("price"))
                }
            }
        }

        fun isConnected(): Boolean = instance != null

        fun isAutomationRunning(): Boolean = instance?.automationSessionActive == true

        fun getCaptchaPendingState(): Map<String, Any> {
            val service = instance ?: return emptyMap()
            return service.readCaptchaPendingSummary()
        }

        fun resumeCaptchaPending(): Boolean {
            val service = instance ?: return false
            return service.resumeCaptchaPendingTasks()
        }

        fun clearCaptchaPending(): Boolean {
            val service = instance ?: return false
            service.clearCaptchaPendingState()
            return true
        }

        fun getLastMeituanCapture(): Map<String, Any> {
            val service = instance ?: return emptyMap()
            val raw = service.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(LAST_MEITUAN_CAPTURE, null) ?: return emptyMap()
            val item = JSONObject(raw)
            return mutableMapOf<String, Any>(
                "taskId" to item.optString("taskId"),
                "hotelName" to item.optString("hotelName"),
                "screenshotPath" to item.optString("screenshotPath"),
                "capturedAt" to item.optLong("capturedAt"),
                "status" to item.optString("status", if (item.has("price")) "success" else "error"),
            ).apply {
                if (item.has("price")) put("price", item.optInt("price"))
            }
        }
    }

    private enum class Stage {
        RESET_TO_HOME,
        FIND_GLOBAL_SEARCH,
        TYPE_QUERY,
        SUBMIT_SEARCH,
        WAIT_SUGGESTION,
        OPEN_HOTEL,
        CITY,
        CITY_SEARCH,
        HOTEL,
        HOTEL_SEARCH,
        GUESTS,
        GUEST_DIALOG,
        VERIFY,
        RESULT,
        HOTEL_DETAIL,
        COLLECT_ROOMS,
    }

    private data class RoomPrice(val name: String, val price: Int, val top: Int)
    private data class VisibleText(val text: String, val top: Int)
    private data class BatchTask(val taskId: String, val city: String, val hotelName: String)
    private data class OcrLine(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
    }
    private data class OcrHotelCandidate(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val score: Double,
    ) {
        val centerX: Int get() = (left + right) / 2
    }

    private data class UpdatePopupGuardState(
        var initialOcrCompleted: Boolean = false,
        var ocrInProgress: Boolean = false,
        var dismissAttempts: Int = 0,
        var unresolvedAttempts: Int = 0,
        var blocked: Boolean = false,
    )

    private val handler = Handler(Looper.getMainLooper())
    private val collectedRooms = linkedMapOf<String, Int>()
    private var navigating = false
    private var attempts = 0
    private var backSent = false
    private var stage = Stage.FIND_GLOBAL_SEARCH
    private var targetCity: String? = null
    private var targetHotel: String? = null
    private var searchTextEntered = false
    private var adultIncremented = false
    private var scrollAttempts = 0
    private var resultInProgress = false
    private var ctripOcrInProgress = false
    private var ctripOcrAttempts = 0
    private var ctripHotelCategoryOpened = false
    private var typedLength = 0
    private var lastCtripSource: AccessibilityNodeInfo? = null
    private var lastCtripWindowClass = ""
    private var ctripHomeResetRequired = false
    private var ctripHomeResetBackAttempts = 0
    private var ctripHomeResetReopenAttempts = 0
    private var ctripHomeResetLastActionAt = 0L
    private var lastMeituanSource: AccessibilityNodeInfo? = null
    private var lastMeituanWindowClass = ""
    private var meituanNavigating = false
    private var meituanStage = 0
    private var meituanHotelName = ""
    private var meituanOcrInProgress = false
    private var meituanOcrAttempts = 0
    private var meituanSearchOcrInProgress = false
    private var meituanSearchOcrAttempts = 0
    private var meituanRunScheduled = false
    private var meituanForegroundMisses = 0
    private var meituanForegroundLaunchAttempts = 0
    private var meituanSearchResultAttempts = 0
    private var meituanDetailMatchAttempts = 0
    private var meituanSearchRetryRound = 0
    private val ctripUpdatePopupGuard = UpdatePopupGuardState()
    private val meituanUpdatePopupGuard = UpdatePopupGuardState()
    private var captchaHandlingInProgress = false
    private var lastCtripCaptchaEventAt = 0L
    private var lastMeituanCaptchaEventAt = 0L
    private val batchQueue = ArrayDeque<BatchTask>()
    private var currentBatchTask: BatchTask? = null
    private val meituanBatchQueue = ArrayDeque<BatchTask>()
    private var currentMeituanBatchTask: BatchTask? = null
    private var combinedRunActive = false
    private var pendingCombinedMeituanTasks: List<Map<String, String>> = emptyList()
    private var automationSessionActive = false
    private var screenOffReceiverRegistered = false
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF && automationSessionActive) {
                abortAutomation("Screen turned off")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        registerScreenOffReceiver()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName == "com.sankuai.meituan") {
            lastMeituanSource = rootInActiveWindow ?: event.source
            if (isCaptchaWindowClass(event.className?.toString().orEmpty())) {
                lastMeituanCaptchaEventAt = SystemClock.uptimeMillis()
            }
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                lastMeituanWindowClass = event.className?.toString().orEmpty()
            }
            Log.d(
                "HotelAutomation",
                "meituan event type=${event.eventType} class=${event.className} " +
                    "text=${event.text.joinToString(" | ")} description=${event.contentDescription} " +
                    "source=${describeNode(event.source)}",
            )
            if (meituanNavigating) {
                scheduleMeituan(350)
            }
        }
        if (event?.packageName == "ctrip.android.view") {
            val source = event.source
            if (isCaptchaWindowClass(event.className?.toString().orEmpty())) {
                lastCtripCaptchaEventAt = SystemClock.uptimeMillis()
            }
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                lastCtripWindowClass = event.className?.toString().orEmpty()
            }
            Log.d(
                "HotelAutomation",
                "event type=${event.eventType} class=${event.className} " +
                    "rootNull=${rootInActiveWindow == null} source=${describeNode(source)}",
            )
            source?.let { lastCtripSource = it }
            if (navigating) scheduleSearch(350)
        }
    }

    override fun onInterrupt() {
        Log.w(
            "HotelAutomation",
            "Accessibility interrupted active=$automationSessionActive " +
                "ctrip=$navigating meituan=$meituanNavigating stage=$stage " +
                "meituanStage=$meituanStage",
        )
        if (automationSessionActive) abortAutomation("Accessibility service interrupted")
        else stopNavigation()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        if (screenOffReceiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            screenOffReceiverRegistered = false
        }
        stopNavigation()
        super.onDestroy()
    }

    private fun startNavigation() {
        handler.removeCallbacksAndMessages(null)
        automationSessionActive = true
        navigating = true
        attempts = 0
        backSent = false
        scheduleSearch(1500)
    }

    private fun scheduleSearch(delayMillis: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ runCurrentStage() }, delayMillis)
    }

    private fun runCurrentStage() {
        if (!navigating) return
        attempts += 1
        val activeRoot = rootInActiveWindow
        val root = activeRoot ?: lastCtripSource
        Log.d(
            "HotelAutomation",
            "run stage=$stage attempt=$attempts activeRootNull=${activeRoot == null} " +
            "fallback=${activeRoot == null && root != null} node=${describeNode(root)}",
        )
        if (checkCaptchaPage("携程", root)) return
        if (checkUpdatePopup(
                platform = "携程",
                root = root,
                allowInitialOcr = stage == Stage.RESET_TO_HOME || stage == Stage.FIND_GLOBAL_SEARCH,
            )
        ) return
        when (stage) {
            Stage.RESET_TO_HOME -> resetCtripToHome(root)
            Stage.FIND_GLOBAL_SEARCH -> findGlobalSearch(root)
            Stage.TYPE_QUERY -> typeHotelQuery(root)
            Stage.SUBMIT_SEARCH -> submitGlobalSearch(root)
            Stage.WAIT_SUGGESTION -> if (root != null) captureHotelSuggestion(root)
            Stage.RESULT -> captureHotelResultPrice(root)
            else -> stopNavigation()
        }
        scheduleOrStop()
    }

    private fun resetCtripToHome(root: AccessibilityNodeInfo?) {
        if (isCtripHome(root)) {
            ctripHomeResetRequired = false
            ctripHomeResetBackAttempts = 0
            ctripHomeResetReopenAttempts = 0
            Log.d("HotelAutomation", "Ctrip home confirmed; begin hotel collection")
            moveTo(Stage.FIND_GLOBAL_SEARCH)
            return
        }

        val now = SystemClock.uptimeMillis()
        if (now - ctripHomeResetLastActionAt < 1200L) return
        val foregroundPackage = root?.packageName?.toString().orEmpty()
        when (foregroundPackage) {
            "ctrip.android.view" -> {
                if (ctripHomeResetBackAttempts >= 6) {
                    failCtripHomeReset("Ctrip home was not reached after 6 back actions")
                    return
                }
                ctripHomeResetBackAttempts += 1
                ctripHomeResetLastActionAt = now
                Log.d(
                    "HotelAutomation",
                    "Ctrip reset back attempt=$ctripHomeResetBackAttempts " +
                        "window=$lastCtripWindowClass",
                )
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            "com.miui.home" -> {
                if (ctripHomeResetReopenAttempts >= 2) {
                    failCtripHomeReset("Ctrip returned to desktop but could not reach its home page")
                    return
                }
                ctripHomeResetReopenAttempts += 1
                ctripHomeResetLastActionAt = now
                val clicked = clickLauncherAppIcon("携程", 0.62f, 0.545f)
                Log.d(
                    "HotelAutomation",
                    "Ctrip reset reopen attempt=$ctripHomeResetReopenAttempts clicked=$clicked",
                )
            }
            else -> {
                ctripHomeResetLastActionAt = now
                Log.d(
                    "HotelAutomation",
                    "Ctrip reset found another foreground package=$foregroundPackage; return desktop",
                )
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    private fun isCtripHome(root: AccessibilityNodeInfo?): Boolean {
        if (root?.packageName?.toString() != "ctrip.android.view") return false
        if (lastCtripWindowClass.contains("CtripHomeActivity")) return true

        val hasSearchEntry = findFirst(root) { node ->
            node.isClickable && nodeText(node).contains("搜索")
        } != null
        val homeMarkers = listOf("酒店", "机票", "火车票", "民宿").count { marker ->
            findFirst(root) { normalize(nodeText(it)) == normalize(marker) } != null
        }
        return hasSearchEntry && homeMarkers >= 2
    }

    private fun failCtripHomeReset(reason: String) {
        Log.w("HotelAutomation", "$reason; end the entire collection")
        performGlobalAction(GLOBAL_ACTION_HOME)
        abortAutomation(reason)
    }

    private fun describeNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return "null"
        return "class=${node.className} text=${node.text} desc=${node.contentDescription} " +
            "children=${node.childCount} clickable=${node.isClickable} editable=${node.isEditable}"
    }

    private fun findGlobalSearch(root: AccessibilityNodeInfo?) {
        if (root != null && findHotelCardPrice(root, targetHotel.orEmpty()) != null) {
            moveTo(Stage.RESULT)
            return
        }
        if (root != null && isGlobalResultPage(root)) {
            if (attempts == 1 || attempts % 4 == 0) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            return
        }
        val editText = root?.let {
            findFirst(it) { node -> node.className?.toString() == "android.widget.EditText" }
        }
        if (editText != null) {
            moveTo(Stage.TYPE_QUERY)
            return
        }

        val searchEntry = root?.let { currentRoot -> findFirst(currentRoot) { node ->
            val text = nodeText(node)
            node.isClickable && (
                text.contains("搜索") ||
                    text.contains("目的地/酒店/景点") ||
                    text.contains("开远的酒店")
                )
        } }
        if (searchEntry != null && clickNodeOrParent(searchEntry)) {
            moveTo(Stage.TYPE_QUERY)
            return
        }

        if (attempts == 1) {
            tapGlobalSearchField()
            moveTo(Stage.TYPE_QUERY)
        }
    }

    private fun isGlobalResultPage(root: AccessibilityNodeInfo): Boolean =
        findFirst(root) { node ->
            nodeText(node).contains("gs_searchlist_view_feeds_v2")
        } != null

    private fun typeHotelQuery(root: AccessibilityNodeInfo?) {
        val hotelName = targetHotel.orEmpty()
        val editText = findAcrossWindows(root) {
            it.className?.toString() == "android.widget.EditText"
        }
        editText?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        if (typedLength == 0) {
            if (editText != null) {
                val arguments = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        hotelName,
                    )
                }
                if (editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                    typedLength = hotelName.length
                    attempts = 0
                    return
                }
            }
            copyHotelName(hotelName)
            typedLength = -1
            tapGlobalSearchField()
            handler.postDelayed({ longPressGlobalSearchField() }, 450)
            return
        }
        if (typedLength == -1) {
            val paste = findAcrossWindows(root) { nodeText(it).contains("粘贴") }
            if (paste != null && clickNodeOrParent(paste)) {
                typedLength = hotelName.length
                attempts = 0
                return
            }
        }
        if (typedLength > 0) moveTo(Stage.SUBMIT_SEARCH)
    }

    private fun submitGlobalSearch(root: AccessibilityNodeInfo?) {
        val searchButton = findAcrossWindows(root) { node ->
            node.isClickable && nodeText(node).trim() == "搜索"
        }
        if (searchButton != null && clickNodeOrParent(searchButton)) {
            moveTo(Stage.RESULT)
            return
        }
        if (attempts == 3) {
            val metrics = resources.displayMetrics
            if (tapScreen(metrics.widthPixels * 0.92f, metrics.heightPixels * 0.07f)) {
                moveTo(Stage.RESULT)
            }
        }
    }

    private fun captureHotelResultPrice(preferredRoot: AccessibilityNodeInfo?) {
        if (!ensureCtripHotelCategory(preferredRoot)) return
        if (!ctripOcrInProgress && ctripOcrAttempts < 3 && ScreenCaptureService.isReady()) {
            runCtripGridOcr()
        }
    }

    private fun ensureCtripHotelCategory(preferredRoot: AccessibilityNodeInfo?): Boolean {
        if (ctripHotelCategoryOpened) return true
        val screenHeight = resources.displayMetrics.heightPixels
        val roots = mutableListOf<AccessibilityNodeInfo>()
        preferredRoot?.let(roots::add)
        windows.mapNotNullTo(roots) { it.root }
        val hotelTab = roots.firstNotNullOfOrNull { root ->
            findFirst(root) { node ->
                if (normalize(nodeText(node)) != "酒店") return@findFirst false
                val bounds = Rect().also(node::getBoundsInScreen)
                bounds.height() > 0 &&
                    bounds.top > screenHeight * 0.08f &&
                    bounds.bottom < screenHeight * 0.22f
            }
        }
        if (hotelTab == null) {
            val hasGridCategoryRow = roots.any { root ->
                findFirst(root) { normalize(nodeText(it)) == "综合" } != null &&
                    findFirst(root) { normalize(nodeText(it)) == "景点" } != null
            }
            if (hasGridCategoryRow && attempts >= 2) {
                val metrics = resources.displayMetrics
                val clicked = tapScreen(
                    metrics.widthPixels * 0.315f,
                    metrics.heightPixels * 0.134f,
                )
                if (clicked) {
                    ctripHotelCategoryOpened = true
                    attempts = 0
                    Log.d("HotelAutomation", "Ctrip fixed hotel category fallback tapped")
                    return false
                }
            }
            // Some Ctrip result layouts have no category row. Preserve the
            // original price flow after giving the page time to finish loading.
            if (attempts >= 3) ctripHotelCategoryOpened = true
            return ctripHotelCategoryOpened
        }
        val bounds = Rect().also(hotelTab::getBoundsInScreen)
        val clicked = clickNodeOrParent(hotelTab) ||
            tapScreen(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        if (!clicked) return false
        ctripHotelCategoryOpened = true
        attempts = 0
        Log.d("HotelAutomation", "Ctrip hotel category selected bounds=$bounds")
        return false
    }

    private fun findHotelCardPrice(
        root: AccessibilityNodeInfo,
        hotelName: String,
        allowGridFallback: Boolean = true,
    ): Int? {
        val expected = normalize(hotelName)
        var result: Int? = null

        // Keep the original rule for Ctrip's ordinary single-column result card,
        // where the hotel name and price are exposed through the same node.
        walk(root) { node ->
            if (result != null) return@walk
            val ownText = nodeText(node)
            if (!normalize(ownText).contains(expected)) return@walk
            result = Regex("[¥￥][^0-9]{0,8}(\\d+)")
                .find(ownText)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }
        if (result != null) return result
        if (!allowGridFallback) return null

        // In Ctrip's two-column grid, wrapped hotel names and prices are unrelated
        // sibling nodes. Pair them by their visible card column and vertical position.
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val matchingNames = mutableListOf<Rect>()
        val prices = mutableListOf<Pair<Rect, Int>>()
        walk(root) { node ->
            val text = nodeText(node)
            val bounds = Rect().also(node::getBoundsInScreen)
            if (bounds.width() <= 0 || bounds.height() <= 0) return@walk
            if (
                bounds.top > screenHeight * 0.18f &&
                normalize(text).contains(expected)
            ) {
                matchingNames.add(bounds)
            }
            val price = Regex("[¥￥]\\s*(\\d+)")
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            if (price != null) prices.add(bounds to price)
        }
        matchingNames.forEach { nameBounds ->
            val nameIsLeft = nameBounds.centerX() < screenWidth / 2
            val nearest = prices
                .asSequence()
                .filter { (bounds, _) ->
                    (bounds.centerX() < screenWidth / 2) == nameIsLeft &&
                        bounds.top >= nameBounds.bottom &&
                        bounds.top - nameBounds.bottom <= screenHeight * 0.35f
                }
                .minByOrNull { (bounds, _) -> bounds.top - nameBounds.bottom }
            if (nearest != null) return nearest.second
        }
        return null
    }

    private fun runCtripGridOcr() {
        ctripOcrInProgress = true
        ctripOcrAttempts += 1
        if (!ScreenCaptureService.recognize { lines ->
                if (!automationSessionActive) {
                    ctripOcrInProgress = false
                    return@recognize
                }
                val hotelName = targetHotel.orEmpty()
                val matchedHotel = findOcrHotelCandidate(
                    rawLines = lines,
                    hotelName = hotelName,
                    minimumTopFraction = 0.18f,
                )
                Log.d(
                    "HotelAutomation",
                    "ctrip screenshot validation hotel=$hotelName candidate=$matchedHotel",
                )
                if (matchedHotel != null) {
                    resultInProgress = true
                    captureCtripScreenshot(hotelName)
                } else {
                    ctripOcrInProgress = false
                    if (navigating) {
                        if (ctripOcrAttempts < 3) scheduleSearch(900)
                        else finishCtripOcrFailure(hotelName)
                    }
                }
            }) {
            ctripOcrInProgress = false
            if (navigating) {
                if (ctripOcrAttempts < 3) scheduleSearch(900)
                else finishCtripOcrFailure(targetHotel.orEmpty())
            }
        }
    }

    private fun captureCtripScreenshot(hotelName: String) {
        if (!ScreenCaptureService.captureScreenshot("ctrip") { screenshotPath ->
                ctripOcrInProgress = false
                if (!automationSessionActive) return@captureScreenshot
                if (screenshotPath != null) {
                    stopNavigation()
                    saveCtripScreenshotResult(hotelName, screenshotPath)
                    finishCapture()
                } else if (ctripOcrAttempts < 3) {
                    resultInProgress = false
                    scheduleSearch(900)
                } else {
                    finishCtripOcrFailure(hotelName)
                }
            }) {
            ctripOcrInProgress = false
            resultInProgress = false
            if (ctripOcrAttempts < 3) scheduleSearch(900)
            else finishCtripOcrFailure(hotelName)
        }
    }

    private fun finishCtripOcrFailure(hotelName: String) {
        if (!navigating) return
        Log.w(
            "HotelAutomation",
            "Ctrip page validation/screenshot failed 3 times; record error: $hotelName",
        )
        stopNavigation()
        val reason = "未确认目标酒店或截图失败"
        val started = ScreenCaptureService.captureScreenshot("ctrip_error") { screenshotPath ->
            if (!automationSessionActive) return@captureScreenshot
            saveCtripErrorResult(hotelName, reason, screenshotPath)
            finishCapture()
        }
        if (!started) {
            saveCtripErrorResult(hotelName, reason)
            finishCapture()
        }
    }

    private fun findCtripGridOcrPrice(
        rawLines: List<Map<String, Any>>,
        hotelName: String,
    ): Int? {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val target = normalizeHotelForMatch(hotelName)
        if (target.isBlank()) return null
        val lines = rawLines.mapNotNull { item ->
            val text = item["text"]?.toString()?.trim().orEmpty()
            val left = item["left"] as? Int ?: return@mapNotNull null
            val top = item["top"] as? Int ?: return@mapNotNull null
            val right = item["right"] as? Int ?: return@mapNotNull null
            val bottom = item["bottom"] as? Int ?: return@mapNotNull null
            if (text.isBlank() || right <= left || bottom <= top) null
            else OcrLine(text, left, top, right, bottom)
        }.filter { it.top > screenHeight * 0.18f }

        val titleStarts = lines.filter { line ->
            val normalized = normalizeHotelForMatch(line.text)
            !line.text.contains('¥') && !line.text.contains('￥') &&
                (containsHotelWord(normalized) || hotelNameSimilarity(target, normalized) >= 0.60)
        }
        val candidates = titleStarts.flatMap { first ->
            val sameColumnFollowing = lines
                .asSequence()
                .filter { next ->
                    next.top >= first.bottom - 6 &&
                        next.top - first.bottom <= screenHeight * 0.065f &&
                        (next.centerX < screenWidth / 2) == (first.centerX < screenWidth / 2) &&
                        !next.text.contains('¥') && !next.text.contains('￥')
                }
                .sortedBy { it.top }
                .take(2)
                .toList()
            buildList {
                add(listOf(first))
                if (sameColumnFollowing.isNotEmpty()) add(listOf(first, sameColumnFollowing[0]))
                if (sameColumnFollowing.size > 1) {
                    add(listOf(first, sameColumnFollowing[0], sameColumnFollowing[1]))
                }
            }.map { parts ->
                val text = parts.joinToString("") { it.text }
                OcrHotelCandidate(
                    text = text,
                    left = parts.minOf { it.left },
                    top = parts.minOf { it.top },
                    right = parts.maxOf { it.right },
                    bottom = parts.maxOf { it.bottom },
                    score = hotelNameSimilarity(target, normalizeHotelForMatch(text)),
                )
            }
        }.sortedByDescending { it.score }
        val best = candidates.firstOrNull() ?: return null
        val runnerUp = candidates.firstOrNull { other ->
            val sameColumn = (other.centerX < screenWidth / 2) == (best.centerX < screenWidth / 2)
            !sameColumn || kotlin.math.abs(other.top - best.top) > screenHeight * 0.10f
        }
        val uniquelyMatched = best.score >= 0.80 &&
            (best.score >= 0.97 || runnerUp == null || best.score - runnerUp.score >= 0.08)
        Log.d(
            "HotelAutomation",
            "ctrip OCR match target=$hotelName best=${best.text}:${best.score} " +
                "runner=${runnerUp?.text}:${runnerUp?.score} unique=$uniquelyMatched",
        )
        if (!uniquelyMatched) return null

        val bestIsLeft = best.centerX < screenWidth / 2
        val nextTitleTop = titleStarts
            .asSequence()
            .filter { next ->
                (next.centerX < screenWidth / 2) == bestIsLeft &&
                    next.top > best.bottom + screenHeight * 0.10f
            }
            .minOfOrNull { it.top }
            ?: screenHeight
        val priceLines = lines
            .asSequence()
            .filter { line ->
                (line.centerX < screenWidth / 2) == bestIsLeft &&
                    line.top >= best.bottom - 10 &&
                    line.top < nextTitleTop
            }
            .mapNotNull { line ->
                val normalizedText = line.text.replace("O", "0")
                val startingValues = Regex("(\\d{2,5})\\s*起")
                    .findAll(normalizedText)
                    .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                    .filter { it in 50..9999 }
                    .toList()
                val currencyValues = Regex("[¥￥]\\s*(\\d{2,5})")
                    .findAll(normalizedText)
                    .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                    .filter { it in 50..9999 }
                    .toList()
                val value = startingValues.lastOrNull() ?: currencyValues.lastOrNull()
                value?.let { Triple(line, it, startingValues.isNotEmpty()) }
            }
            .toList()
        return priceLines
            .sortedWith(
                compareByDescending<Triple<OcrLine, Int, Boolean>> { it.third }
                    .thenBy { it.first.top }
                    .thenByDescending { it.first.left },
            )
            .firstOrNull()
            ?.second
    }

    private fun normalizeHotelForMatch(value: String): String = normalize(value)
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]"), "")
        // Meituan may visually overlay a live-video badge on the hotel title;
        // ML Kit then returns strings such as "1直播中同成都万达瑞华酒店".
        // Remove only this known leading badge so restaurant names containing
        // the hotel name remain distinct and cannot win the match.
        .replace(Regex("^\\d*直播中[同回]?"), "")
        .replace(Regex("(高档型|豪华型|舒适型|经济型|精品型)$"), "")

    private fun containsHotelWord(value: String): Boolean =
        listOf("酒店", "宾馆", "客栈", "民宿", "旅馆", "公寓").any(value::contains)

    private fun hotelNameSimilarity(target: String, candidate: String): Double {
        if (target.isBlank() || candidate.isBlank()) return 0.0
        if (target == candidate) return 1.0
        val shorter = minOf(target.length, candidate.length)
        val longer = maxOf(target.length, candidate.length)
        if ((target.contains(candidate) || candidate.contains(target)) &&
            shorter.toDouble() / longer >= 0.78
        ) return 0.98
        val previous = IntArray(candidate.length + 1) { it }
        for (i in target.indices) {
            var diagonal = previous[0]
            previous[0] = i + 1
            for (j in candidate.indices) {
                val old = previous[j + 1]
                previous[j + 1] = minOf(
                    previous[j + 1] + 1,
                    previous[j] + 1,
                    diagonal + if (target[i] == candidate[j]) 0 else 1,
                )
                diagonal = old
            }
        }
        return 1.0 - previous[candidate.length].toDouble() / maxOf(target.length, candidate.length)
    }

    private fun findAcrossWindows(
        preferredRoot: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        preferredRoot?.let { findFirst(it, predicate)?.let { found -> return found } }
        windows.forEach { window ->
            window.root?.let { findFirst(it, predicate)?.let { found -> return found } }
        }
        return null
    }

    private fun updatePopupState(platform: String): UpdatePopupGuardState =
        if (platform == "携程") ctripUpdatePopupGuard else meituanUpdatePopupGuard

    private fun resetUpdatePopupGuard(platform: String) {
        val state = updatePopupState(platform)
        state.initialOcrCompleted = false
        state.ocrInProgress = false
        state.dismissAttempts = 0
        state.unresolvedAttempts = 0
        state.blocked = false
    }

    private fun isUpdatePromptText(value: String): Boolean {
        val text = normalize(value)
        if (text.isBlank()) return false
        return listOf(
            "发现新版本",
            "检测到新版本",
            "有新版本",
            "版本更新",
            "应用升级",
            "新版本上线",
            "立即更新",
            "马上更新",
            "现在更新",
            "前往更新",
            "去更新",
            "升级到最新版本",
            "更新到最新版本",
            "更新至最新版本",
        ).any(text::contains)
    }

    private fun isSafeUpdateDismissText(value: String): Boolean {
        val text = normalize(value)
        return listOf(
            "暂不更新",
            "暂不升级",
            "以后再说",
            "下次再说",
            "稍后更新",
            "稍后再说",
            "暂不",
            "稍后",
            "取消",
            "关闭",
            "跳过",
            "我知道了",
        ).any { safe -> text == normalize(safe) }
    }

    private fun checkUpdatePopup(
        platform: String,
        root: AccessibilityNodeInfo?,
        allowInitialOcr: Boolean,
    ): Boolean {
        val state = updatePopupState(platform)
        if (state.blocked || state.ocrInProgress) return true

        val prompt = findAcrossWindows(root) { isUpdatePromptText(nodeText(it)) }
        if (prompt != null) {
            if (state.dismissAttempts >= 2) {
                failBlockingUpdatePopup(platform, "更新弹窗连续两次未能关闭")
                return true
            }
            val safeButton = findAcrossWindows(root) {
                isSafeUpdateDismissText(nodeText(it))
            }
            if (safeButton != null && clickNodeOrParent(safeButton)) {
                state.dismissAttempts += 1
                state.unresolvedAttempts = 0
                state.initialOcrCompleted = false
                Log.d(
                    "HotelAutomation",
                    "$platform update popup dismissed by accessibility " +
                        "attempt=${state.dismissAttempts} text=${nodeText(safeButton)}",
                )
                resumeAfterUpdatePopupGuard(platform, 1200)
                return true
            }
            startUpdatePopupOcr(platform, state, knownPrompt = true)
            return true
        }

        if (allowInitialOcr && !state.initialOcrCompleted) {
            startUpdatePopupOcr(platform, state, knownPrompt = false)
            return true
        }
        return false
    }

    private fun startUpdatePopupOcr(
        platform: String,
        state: UpdatePopupGuardState,
        knownPrompt: Boolean,
    ) {
        if (state.ocrInProgress || state.blocked) return
        state.ocrInProgress = true
        val started = ScreenCaptureService.recognize { rawLines ->
            state.ocrInProgress = false
            if (!automationSessionActive || state.blocked) return@recognize
            val lines = rawLines.mapNotNull { item ->
                val text = item["text"]?.toString().orEmpty()
                val left = (item["left"] as? Number)?.toInt() ?: return@mapNotNull null
                val top = (item["top"] as? Number)?.toInt() ?: return@mapNotNull null
                val right = (item["right"] as? Number)?.toInt() ?: return@mapNotNull null
                val bottom = (item["bottom"] as? Number)?.toInt() ?: return@mapNotNull null
                OcrLine(text, left, top, right, bottom)
            }
            val combinedText = lines.joinToString("\n") { it.text }
            if (!isUpdatePromptText(combinedText)) {
                state.initialOcrCompleted = true
                state.unresolvedAttempts = 0
                Log.d("HotelAutomation", "$platform initial update-popup OCR check passed")
                resumeAfterUpdatePopupGuard(platform, 250)
                return@recognize
            }

            if (state.dismissAttempts >= 2) {
                failBlockingUpdatePopup(platform, "更新弹窗连续两次未能关闭")
                return@recognize
            }
            val safeLine = lines
                .filter { isSafeUpdateDismissText(it.text) }
                .maxByOrNull { it.top }
            if (safeLine != null && tapScreen(safeLine.centerX.toFloat(), safeLine.centerY.toFloat())) {
                state.dismissAttempts += 1
                state.unresolvedAttempts = 0
                state.initialOcrCompleted = false
                Log.d(
                    "HotelAutomation",
                    "$platform update popup dismissed by OCR attempt=${state.dismissAttempts} " +
                        "text=${safeLine.text}",
                )
                resumeAfterUpdatePopupGuard(platform, 1200)
                return@recognize
            }

            state.unresolvedAttempts += 1
            if (state.unresolvedAttempts >= 2) {
                failBlockingUpdatePopup(platform, "检测到更新弹窗，但没有可安全点击的稍后/取消按钮")
            } else {
                state.initialOcrCompleted = false
                resumeAfterUpdatePopupGuard(platform, 900)
            }
        }
        if (!started) {
            state.ocrInProgress = false
            if (knownPrompt) {
                failBlockingUpdatePopup(platform, "检测到更新弹窗，但屏幕识别服务不可用")
            } else {
                state.initialOcrCompleted = true
                resumeAfterUpdatePopupGuard(platform, 250)
            }
        }
    }

    private fun resumeAfterUpdatePopupGuard(platform: String, delayMillis: Long) {
        if (!automationSessionActive) return
        if (platform == "携程" && navigating) scheduleSearch(delayMillis)
        else if (platform == "美团" && meituanNavigating) scheduleMeituan(delayMillis)
    }

    private fun failBlockingUpdatePopup(platform: String, reason: String) {
        val state = updatePopupState(platform)
        if (state.blocked || !automationSessionActive) return
        state.blocked = true
        state.ocrInProgress = false
        navigating = false
        meituanNavigating = false
        handler.removeCallbacksAndMessages(null)
        Log.w("HotelAutomation", "$platform update popup blocked collection: $reason")

        fun persistAndAbort(screenshotPath: String?) {
            if (!automationSessionActive) return
            if (platform == "携程") {
                saveCtripErrorResult(
                    hotelName = currentBatchTask?.hotelName ?: targetHotel.orEmpty(),
                    reason = reason,
                    screenshotPath = screenshotPath,
                    status = "update_blocked",
                )
            } else {
                val data = JSONObject()
                    .put("hotelName", currentMeituanBatchTask?.hotelName ?: meituanHotelName)
                    .put("status", "update_blocked")
                    .put("reason", reason)
                    .put("capturedAt", System.currentTimeMillis())
                if (!screenshotPath.isNullOrBlank()) data.put("screenshotPath", screenshotPath)
                currentMeituanBatchTask?.let { data.put("taskId", it.taskId) }
                persistMeituanCapture(data)
            }
            abortAutomation("$platform update popup blocked collection")
            playWarningSound()
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        val started = ScreenCaptureService.captureScreenshot("${platform}_update_blocked") {
            persistAndAbort(it)
        }
        if (!started) persistAndAbort(null)
    }

    private fun isCaptchaWindowClass(value: String): Boolean {
        val className = value.lowercase()
        return listOf(
            "yoda",
            "captcha",
            "geetest",
            "verifyactivity",
            "verificationactivity",
        ).any(className::contains)
    }

    private fun isCaptchaPromptText(value: String): Boolean {
        val text = normalize(value)
        return listOf(
            "请按语序依次点击",
            "请依次点击下图",
            "拖动滑块完成拼图",
            "请完成安全验证",
            "请完成验证",
            "安全验证未通过",
            "验证码",
        ).any(text::contains)
    }

    private fun checkCaptchaPage(platform: String, root: AccessibilityNodeInfo?): Boolean {
        if (captchaHandlingInProgress) return true
        if (!automationSessionActive) return false
        val now = SystemClock.uptimeMillis()
        val recentCaptchaWindow = if (platform == "携程") {
            now - lastCtripCaptchaEventAt < 10_000L
        } else {
            now - lastMeituanCaptchaEventAt < 10_000L
        }
        val promptVisible = findAcrossWindows(root) {
            isCaptchaPromptText(nodeText(it))
        } != null
        if (!recentCaptchaWindow && !promptVisible) return false

        handleCaptchaDetected(platform)
        return true
    }

    private fun batchTaskToJson(task: BatchTask): JSONObject = JSONObject()
        .put("taskId", task.taskId)
        .put("city", task.city)
        .put("hotelName", task.hotelName)

    private fun mapTaskToJson(task: Map<String, String>): JSONObject = JSONObject()
        .put("taskId", task["taskId"].orEmpty())
        .put("city", task["city"].orEmpty())
        .put("hotelName", task["hotelName"].orEmpty())

    private fun captchaPendingTasks(platform: String): Pair<JSONArray, JSONArray> {
        val ctrip = JSONArray()
        val meituan = JSONArray()
        if (platform == "携程") {
            currentBatchTask?.let { ctrip.put(batchTaskToJson(it)) }
            batchQueue.forEach { ctrip.put(batchTaskToJson(it)) }
            pendingCombinedMeituanTasks.forEach { meituan.put(mapTaskToJson(it)) }
        } else {
            currentMeituanBatchTask?.let { meituan.put(batchTaskToJson(it)) }
            meituanBatchQueue.forEach { meituan.put(batchTaskToJson(it)) }
        }
        return ctrip to meituan
    }

    private fun handleCaptchaDetected(platform: String) {
        if (captchaHandlingInProgress || !automationSessionActive) return
        captchaHandlingInProgress = true
        navigating = false
        meituanNavigating = false
        handler.removeCallbacksAndMessages(null)
        val (ctripTasks, meituanTasks) = captchaPendingTasks(platform)
        val hotelName = if (platform == "携程") {
            currentBatchTask?.hotelName ?: targetHotel.orEmpty()
        } else {
            currentMeituanBatchTask?.hotelName ?: meituanHotelName
        }
        val taskId = if (platform == "携程") {
            currentBatchTask?.taskId
        } else {
            currentMeituanBatchTask?.taskId
        }
        Log.w(
            "HotelAutomation",
            "$platform captcha detected hotel=$hotelName " +
                "ctripPending=${ctripTasks.length()} meituanPending=${meituanTasks.length()}",
        )

        fun persistAndPause(screenshotPath: String?) {
            if (!automationSessionActive) return
            val capturedAt = System.currentTimeMillis()
            val pendingState = JSONObject()
                .put("platform", platform)
                .put("hotelName", hotelName)
                .put("capturedAt", capturedAt)
                .put("ctripTasks", ctripTasks)
                .put("meituanTasks", meituanTasks)
            if (!screenshotPath.isNullOrBlank()) {
                pendingState.put("screenshotPath", screenshotPath)
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(CAPTCHA_PENDING_STATE, pendingState.toString())
                .apply()

            if (platform == "携程") {
                saveCtripErrorResult(
                    hotelName = hotelName,
                    reason = "平台要求人工完成验证码",
                    screenshotPath = screenshotPath,
                    status = "verification_required",
                )
            } else {
                val data = JSONObject()
                    .put("hotelName", hotelName)
                    .put("status", "verification_required")
                    .put("reason", "平台要求人工完成验证码")
                    .put("capturedAt", capturedAt)
                if (!screenshotPath.isNullOrBlank()) data.put("screenshotPath", screenshotPath)
                if (!taskId.isNullOrBlank()) data.put("taskId", taskId)
                persistMeituanCapture(data)
            }
            abortAutomation("$platform captcha requires human verification")
            playWarningSound()
            // Keep the captcha visible. The user completes it manually, then
            // returns to the monitor app and resumes only the saved tasks.
        }

        val started = ScreenCaptureService.captureScreenshot("${platform}_captcha") {
            persistAndPause(it)
        }
        if (!started) persistAndPause(null)
    }

    private fun jsonTasks(array: JSONArray): List<Map<String, String>> =
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val taskId = item.optString("taskId")
            val hotelName = item.optString("hotelName")
            if (taskId.isBlank() || hotelName.isBlank()) return@mapNotNull null
            mapOf(
                "taskId" to taskId,
                "city" to item.optString("city"),
                "hotelName" to hotelName,
            )
        }

    private fun readCaptchaPendingSummary(): Map<String, Any> {
        val raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(CAPTCHA_PENDING_STATE, null) ?: return emptyMap()
        val state = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val ctripCount = state.optJSONArray("ctripTasks")?.length() ?: 0
        val meituanCount = state.optJSONArray("meituanTasks")?.length() ?: 0
        return mutableMapOf<String, Any>(
            "platform" to state.optString("platform"),
            "hotelName" to state.optString("hotelName"),
            "capturedAt" to state.optLong("capturedAt"),
            "pendingCount" to ctripCount + meituanCount,
        ).apply {
            val screenshotPath = state.optString("screenshotPath")
            if (screenshotPath.isNotBlank()) put("screenshotPath", screenshotPath)
        }
    }

    private fun clearCaptchaPendingState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .remove(CAPTCHA_PENDING_STATE)
            .apply()
        captchaHandlingInProgress = false
    }

    private fun resumeCaptchaPendingTasks(): Boolean {
        if (automationSessionActive) return false
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val raw = preferences.getString(CAPTCHA_PENDING_STATE, null) ?: return false
        val state = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        val ctripTasks = jsonTasks(state.optJSONArray("ctripTasks") ?: JSONArray())
        val meituanTasks = jsonTasks(state.optJSONArray("meituanTasks") ?: JSONArray())
        if (ctripTasks.isEmpty() && meituanTasks.isEmpty()) return false

        clearCaptchaPendingState()
        captchaHandlingInProgress = false
        return when {
            ctripTasks.isNotEmpty() -> {
                combinedRunActive = meituanTasks.isNotEmpty()
                pendingCombinedMeituanTasks = meituanTasks.map { it.toMap() }
                requireCtripHomeReset()
                runFromLauncher("携程", 0.62f, 0.545f) {
                    startBatch(ctripTasks, resetCaptures = false)
                }
                true
            }
            else -> {
                combinedRunActive = false
                pendingCombinedMeituanTasks = emptyList()
                runFromLauncher("美团", 0.62f, 0.655f) {
                    startMeituanBatchQueue(
                        meituanTasks,
                        firstTaskDelayMillis = 2200,
                        resetCaptures = false,
                    )
                }
                true
            }
        }
    }

    private fun copyHotelName(hotelName: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("hotel_name", hotelName))
    }

    private fun tapGlobalSearchField() = dispatchSearchGesture(80L)

    private fun longPressGlobalSearchField() = dispatchSearchGesture(900L)

    private fun dispatchSearchGesture(durationMillis: Long) {
        val metrics = resources.displayMetrics
        val path = Path().apply {
            moveTo(metrics.widthPixels * 0.45f, metrics.heightPixels * 0.07f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMillis))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun openHotelEntry(root: AccessibilityNodeInfo) {
        if (findByText(root, "城市") != null) {
            if (targetCity == null) stopNavigation() else moveTo(Stage.CITY)
            return
        }
        val hotelNode = findByExactText(root, "酒店")
        if (hotelNode != null && clickNodeOrParent(hotelNode)) {
            if (targetCity == null) stopNavigation() else moveTo(Stage.CITY)
            return
        }
        if (attempts == 4 && !backSent) {
            backSent = true
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun openCitySelector(root: AccessibilityNodeInfo) {
        val city = targetCity.orEmpty()
        if (findByExactText(root, city) != null && findByText(root, "位置/品牌/酒店") != null) {
            moveTo(Stage.GUESTS)
            return
        }
        val cityLabel = findByExactText(root, "城市")
        if (cityLabel != null && clickNodeOrParent(cityLabel)) moveTo(Stage.CITY_SEARCH)
    }

    private fun openHotelSearch(root: AccessibilityNodeInfo) {
        val field = findByText(root, "位置/品牌/酒店")
        if (field != null && clickNodeOrParent(field)) moveTo(Stage.HOTEL_SEARCH)
    }

    private fun searchAndSelect(root: AccessibilityNodeInfo, value: String, nextStage: Stage) {
        val editText = findFirst(root) { it.className?.toString() == "android.widget.EditText" }
        if (editText != null && !searchTextEntered) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
            editText.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            if (editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                searchTextEntered = true
                return
            }
        }
        val resultNode = findByNormalizedText(root, value, excludeEditText = true)
        if (resultNode != null && clickNodeOrParent(resultNode)) moveTo(nextStage)
    }

    private fun captureHotelSuggestion(root: AccessibilityNodeInfo) {
        val value = targetHotel.orEmpty()
        if (findByText(root, "欢迎度排序") != null || findByText(root, "位置距离") != null) return
        val match = findSuggestionPrice(root, value) ?: return
        resultInProgress = true
        stopNavigation()
        savePriceResult(match.first, match.second)
        finishCapture()
    }

    private fun findSuggestionPrice(root: AccessibilityNodeInfo, hotelName: String): Pair<String, Int>? {
        var result: Pair<String, Int>? = null
        val expected = normalize(hotelName)
        walk(root) { node ->
            if (result != null) return@walk
            val ownText = nodeText(node)
            if (!normalize(ownText).contains(expected)) return@walk
            var container: AccessibilityNodeInfo? = node
            repeat(6) {
                val current = container ?: return@repeat
                val combined = buildString { walk(current) { append(nodeText(it)).append('\n') } }
                val prices = Regex("(?:参考价)?[¥￥]\\s*(\\d+)").findAll(combined)
                    .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                    .toList()
                if (prices.isNotEmpty()) {
                    result = hotelName to prices.last()
                    return@walk
                }
                container = current.parent
            }
        }
        return result
    }

    private fun savePriceResult(hotelName: String, price: Int) {
        val data = JSONObject()
            .put("hotelName", hotelName)
            .put("price", price)
            .put("status", "success")
            .put("capturedAt", System.currentTimeMillis())
        currentBatchTask?.let { data.put("taskId", it.taskId) }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putString(LAST_CTRIP_CAPTURE, data.toString()).apply()
        if (currentBatchTask != null) {
            val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val captures = JSONArray(preferences.getString(BATCH_CTRIP_CAPTURES, "[]"))
            captures.put(data)
            preferences.edit().putString(BATCH_CTRIP_CAPTURES, captures.toString()).apply()
        }
    }

    private fun saveCtripScreenshotResult(hotelName: String, screenshotPath: String) {
        val data = JSONObject()
            .put("hotelName", hotelName)
            .put("screenshotPath", screenshotPath)
            .put("status", "success")
            .put("capturedAt", System.currentTimeMillis())
        currentBatchTask?.let { data.put("taskId", it.taskId) }
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        preferences.edit().putString(LAST_CTRIP_CAPTURE, data.toString()).apply()
        if (currentBatchTask != null) {
            val captures = JSONArray(preferences.getString(BATCH_CTRIP_CAPTURES, "[]"))
            captures.put(data)
            preferences.edit().putString(BATCH_CTRIP_CAPTURES, captures.toString()).apply()
        }
    }

    private fun saveCtripErrorResult(
        hotelName: String,
        reason: String,
        screenshotPath: String? = null,
        status: String = "error",
    ) {
        val data = JSONObject()
            .put("hotelName", hotelName)
            .put("status", status)
            .put("reason", reason)
            .put("capturedAt", System.currentTimeMillis())
        if (!screenshotPath.isNullOrBlank()) data.put("screenshotPath", screenshotPath)
        currentBatchTask?.let { data.put("taskId", it.taskId) }
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        preferences.edit().putString(LAST_CTRIP_CAPTURE, data.toString()).apply()
        if (currentBatchTask != null) {
            val captures = JSONArray(preferences.getString(BATCH_CTRIP_CAPTURES, "[]"))
            captures.put(data)
            preferences.edit().putString(BATCH_CTRIP_CAPTURES, captures.toString()).apply()
        }
    }

    private fun startStandaloneCtripBatch(tasks: List<Map<String, String>>) {
        clearCaptchaPendingState()
        combinedRunActive = false
        pendingCombinedMeituanTasks = emptyList()
        requireCtripHomeReset()
        runFromLauncher("携程", 0.62f, 0.545f) { startBatch(tasks) }
    }

    private fun startCombinedBatchQueues(
        ctripTasks: List<Map<String, String>>,
        meituanTasks: List<Map<String, String>>,
    ) {
        clearCaptchaPendingState()
        combinedRunActive = true
        pendingCombinedMeituanTasks = meituanTasks.map { it.toMap() }
        requireCtripHomeReset()
        runFromLauncher("携程", 0.62f, 0.545f) { startBatch(ctripTasks) }
    }

    private fun startCtripFormFromLauncher(city: String, hotelName: String) {
        clearCaptchaPendingState()
        combinedRunActive = false
        pendingCombinedMeituanTasks = emptyList()
        requireCtripHomeReset()
        runFromLauncher("携程", 0.62f, 0.545f) { startPreparation(city, hotelName) }
    }

    private fun requireCtripHomeReset() {
        ctripHomeResetRequired = true
        ctripHomeResetBackAttempts = 0
        ctripHomeResetReopenAttempts = 0
        ctripHomeResetLastActionAt = 0L
        lastCtripWindowClass = ""
    }

    private fun startBatch(
        tasks: List<Map<String, String>>,
        resetCaptures: Boolean = true,
    ) {
        batchQueue.clear()
        tasks.mapNotNullTo(batchQueue) { task ->
            val id = task["taskId"].orEmpty()
            val city = task["city"].orEmpty()
            val hotelName = task["hotelName"].orEmpty()
            if (id.isBlank() || city.isBlank() || hotelName.isBlank()) null
            else BatchTask(id, city, hotelName)
        }
        if (resetCaptures) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putString(BATCH_CTRIP_CAPTURES, "[]").apply()
        }
        currentBatchTask = null
        startNextBatchTask(first = true)
    }

    private fun finishCapture() {
        if (currentBatchTask == null) {
            stopNavigation()
            playCompletionSound()
            returnToMonitorApp()
            return
        }
        startNextBatchTask(first = false)
    }

    private fun startNextBatchTask(first: Boolean) {
        stopNavigation()
        val next = batchQueue.removeFirstOrNull()
        if (next == null) {
            currentBatchTask = null
            if (startPendingCombinedMeituanBatch()) return
            playCompletionSound()
            returnToMonitorApp()
            return
        }
        currentBatchTask = next
        handler.postDelayed({ startPreparation(next.city, next.hotelName) }, if (first) 500 else 2500)
    }

    private fun startPendingCombinedMeituanBatch(): Boolean {
        if (!combinedRunActive || pendingCombinedMeituanTasks.isEmpty()) return false
        val tasks = pendingCombinedMeituanTasks
        pendingCombinedMeituanTasks = emptyList()
        // Ctrip's result activity can reclaim the foreground immediately after a
        // direct cross-app launch. Go through Home first so Meituan becomes the
        // unambiguous foreground app, then allow its home screen to settle.
        runFromLauncher("美团", 0.62f, 0.655f) {
            startMeituanBatchQueue(tasks, 2200)
        }
        return true
    }

    private fun startMeituanBatchFromLauncher(tasks: List<Map<String, String>>) {
        clearCaptchaPendingState()
        runFromLauncher("美团", 0.62f, 0.655f) {
            startMeituanBatchQueue(tasks, 2200)
        }
    }

    private fun startMeituanHotelFromLauncher(hotelName: String) {
        clearCaptchaPendingState()
        combinedRunActive = false
        pendingCombinedMeituanTasks = emptyList()
        runFromLauncher("美团", 0.62f, 0.655f) { prepareFirstMeituanSearch(hotelName) }
    }

    private fun runFromLauncher(
        appLabel: String,
        fallbackX: Float,
        fallbackY: Float,
        afterLaunch: () -> Unit,
    ) {
        automationSessionActive = true
        captchaHandlingInProgress = false
        if (appLabel == "携程") lastCtripCaptchaEventAt = 0L
        else lastMeituanCaptchaEventAt = 0L
        resetUpdatePopupGuard(appLabel)
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            val clicked = clickLauncherAppIcon(appLabel, fallbackX, fallbackY)
            Log.d("HotelAutomation", "launcher app=$appLabel icon clicked=$clicked")
            handler.postDelayed(afterLaunch, 1800)
        }, 1500)
    }

    private fun clickLauncherAppIcon(
        appLabel: String,
        fallbackX: Float,
        fallbackY: Float,
    ): Boolean {
        val root = rootInActiveWindow
        val icon = findAcrossWindows(root) {
            normalize(nodeText(it)).contains(normalize(appLabel)) && it.isClickable
        }
        if (icon != null && clickNodeOrParent(icon)) return true

        // Xiaomi launcher on the dedicated idle phone may not expose icon text
        // through accessibility. Use the observed stable grid position as a
        // proportional fallback so it remains valid across screen densities.
        val metrics = resources.displayMetrics
        return tapScreen(
            metrics.widthPixels * fallbackX,
            metrics.heightPixels * fallbackY,
        )
    }

    private fun playCompletionSound() {
        val custom = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(COMPLETION_SOUND_URI, null)
        val uri = custom?.let(android.net.Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        try {
            RingtoneManager.getRingtone(applicationContext, uri)?.play()
        } catch (_: Exception) {
            val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(applicationContext, fallback)?.play()
        }
    }

    private fun playWarningSound() {
        val alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        try {
            RingtoneManager.getRingtone(applicationContext, alarm ?: fallback)?.play()
        } catch (_: Exception) {
            RingtoneManager.getRingtone(applicationContext, fallback)?.play()
        }
    }

    private fun returnToMonitorApp() {
        // Completion ends on the Android desktop. The user can inspect the
        // report later without the service trying to reopen the app.
        automationSessionActive = false
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun registerScreenOffReceiver() {
        if (screenOffReceiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenOffReceiver, filter)
        }
        screenOffReceiverRegistered = true
    }

    private fun abortAutomation(reason: String) {
        Log.w("HotelAutomation", "$reason; abort all automation immediately")
        automationSessionActive = false
        navigating = false
        meituanNavigating = false
        ctripOcrInProgress = false
        meituanOcrInProgress = false
        meituanSearchOcrInProgress = false
        ctripUpdatePopupGuard.ocrInProgress = false
        meituanUpdatePopupGuard.ocrInProgress = false
        meituanRunScheduled = false
        combinedRunActive = false
        pendingCombinedMeituanTasks = emptyList()
        batchQueue.clear()
        meituanBatchQueue.clear()
        currentBatchTask = null
        currentMeituanBatchTask = null
        lastCtripSource = null
        lastMeituanSource = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun openGuestSelector(root: AccessibilityNodeInfo) {
        if (findByText(root, "2成人") != null) {
            moveTo(Stage.HOTEL)
            return
        }
        val guestNode = findFirst(root) { nodeText(it).matches(Regex("\\d+间房\\s+\\d+成人.*")) }
        if (guestNode != null && clickNodeOrParent(guestNode)) moveTo(Stage.GUEST_DIALOG)
    }

    private fun setAdultCount(root: AccessibilityNodeInfo) {
        if (!adultIncremented) {
            val adultLabel = findByExactText(root, "成人数") ?: return
            val labelBounds = Rect().also { adultLabel.getBoundsInScreen(it) }
            val plus = findFirst(root) { node ->
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                node.isClickable && node.className?.toString() == "android.widget.ImageView" &&
                    bounds.left >= 900 && kotlin.math.abs(bounds.centerY() - labelBounds.centerY()) < 100
            }
            if (plus?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) adultIncremented = true
            return
        }
        val done = findByExactText(root, "完成")
        if (done != null && clickNodeOrParent(done)) moveTo(Stage.VERIFY)
        else {
            performGlobalAction(GLOBAL_ACTION_BACK)
            moveTo(Stage.VERIFY)
        }
    }

    private fun verifyPreparedForm(root: AccessibilityNodeInfo) {
        if (findByText(root, "2成人") != null) moveTo(Stage.HOTEL)
    }

    private fun openHotelResult(root: AccessibilityNodeInfo) {
        val expected = normalize(targetHotel.orEmpty())
        var target: AccessibilityNodeInfo? = null
        walk(root) { node ->
            if (target != null) return@walk
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            if (bounds.top in 400..1200 && bounds.width() > 600 &&
                normalize(nodeText(node)).contains(expected)
            ) {
                target = node
            }
        }
        val hotel = target ?: return
        val bounds = Rect().also { hotel.getBoundsInScreen(it) }
        if (tapScreen(150f, (bounds.top + 150).toFloat())) moveTo(Stage.HOTEL_DETAIL)
    }

    private fun tapScreen(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun waitForHotelDetail(root: AccessibilityNodeInfo) {
        val onTargetHotel = findByNormalizedText(root, targetHotel.orEmpty()) != null ||
            findByText(root, targetHotel.orEmpty()) != null
        if (!onTargetHotel || findByText(root, "1晚") == null) return

        if (findRoomPrices(root).isNotEmpty()) {
            moveTo(Stage.COLLECT_ROOMS)
            return
        }

        val scrollable = findMainScrollable(root)
        if (scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true) {
            attempts = 0
        }
    }

    private fun collectRoomPrices(root: AccessibilityNodeInfo) {
        findRoomPrices(root)
            .sortedBy { it.top }
            .forEach { room -> collectedRooms.putIfAbsent(normalizeRoomName(room.name), room.price) }

        if (collectedRooms.size >= 4) {
            saveCollectedRooms()
            stopNavigation()
            return
        }

        if (scrollAttempts >= 5) {
            saveCollectedRooms()
            stopNavigation()
            return
        }

        val scrollable = findMainScrollable(root)
        if (scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true) {
            scrollAttempts += 1
            attempts = 0
        }
    }

    private fun findRoomPrices(root: AccessibilityNodeInfo): List<RoomPrice> {
        val rooms = mutableListOf<RoomPrice>()
        walk(root) { node ->
            val description = nodeText(node)
            if (!description.contains("房") || !description.contains('¥')) return@walk
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            if (bounds.width() <= 0 || bounds.height() <= 0) return@walk
            val prices = Regex("¥\\s*(\\d+)").findAll(description)
                .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                .toList()
            if (prices.isEmpty()) return@walk
            val firstLine = description.lineSequence().firstOrNull().orEmpty()
                .replace("￼", "")
                .substringBefore('[')
                .trim()
            if (firstLine.isNotBlank()) rooms += RoomPrice(firstLine, prices.last(), bounds.top)
        }
        return rooms
    }

    private fun findMainScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findFirst(root) { node ->
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            node.isScrollable && bounds.width() >= 900 && bounds.height() >= 800
        }

    private fun saveCollectedRooms() {
        val array = JSONArray()
        collectedRooms.entries.take(4).forEach { (name, price) ->
            array.put(JSONObject().put("name", name).put("price", price))
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putString(LAST_CTRIP_ROOMS, array.toString()).apply()
    }

    private fun captureMeituanDetail(hotelName: String): Map<String, Any> {
        val activeRoot = rootInActiveWindow
        val root = if (activeRoot?.packageName == "com.sankuai.meituan") {
            activeRoot
        } else {
            lastMeituanSource
        } ?: return emptyMap()
        val visible = mutableListOf<VisibleText>()
        walk(root) { node ->
            val text = nodeText(node).trim()
            if (text.isEmpty()) return@walk
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            if (bounds.width() > 0 && bounds.height() > 0) {
                visible += VisibleText(text, bounds.top)
            }
        }

        val excludedRoomNames = setOf("大床房", "双床房", "家庭房")
        val roomNames = visible
            .filter { item ->
                item.text.contains('房') &&
                    item.text.length >= 6 &&
                    item.text !in excludedRoomNames &&
                    !item.text.contains("优选") &&
                    !item.text.contains("入住") &&
                    !item.text.contains("房间")
            }
            .distinctBy { normalizeRoomName(it.text) }
            .sortedBy { it.top }

        val numericPrices = visible
            .mapNotNull { item ->
                val value = item.text.trim().toIntOrNull() ?: return@mapNotNull null
                if (value in 80..9999) item.copy(text = value.toString()) else null
            }
            .sortedBy { it.top }

        val rooms = mutableListOf<Pair<String, Int>>()
        roomNames.forEachIndexed { index, room ->
            val nextTop = roomNames.getOrNull(index + 1)?.top ?: (room.top + 850)
            val prices = numericPrices
                .filter { it.top >= room.top && it.top < nextTop }
                .map { it.text.toInt() }
            if (prices.isNotEmpty()) rooms += room.text to prices.last()
        }
        val firstTwo = rooms.take(2)
        if (firstTwo.size < 2) return emptyMap()

        val capturedAt = System.currentTimeMillis()
        val lowestPrice = firstTwo.minOf { it.second }
        val data = JSONObject()
            .put("hotelName", hotelName)
            .put("price", lowestPrice)
            .put("capturedAt", capturedAt)
            .put("rooms", JSONArray().apply {
                firstTwo.forEach { (name, price) ->
                    put(JSONObject().put("name", name).put("price", price))
                }
            })
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putString(LAST_MEITUAN_CAPTURE, data.toString()).apply()
        return mapOf(
            "hotelName" to hotelName,
            "price" to lowestPrice,
            "capturedAt" to capturedAt,
            "rooms" to firstTwo.map { mapOf("name" to it.first, "price" to it.second) },
        )
    }

    private fun startMeituan(hotelName: String, resetSearchRetry: Boolean = true) {
        handler.removeCallbacksAndMessages(null)
        meituanHotelName = hotelName
        if (resetSearchRetry) meituanSearchRetryRound = 0
        meituanStage = 0
        meituanNavigating = true
        meituanOcrInProgress = false
        meituanOcrAttempts = 0
        meituanSearchOcrInProgress = false
        meituanSearchOcrAttempts = 0
        meituanRunScheduled = false
        meituanForegroundMisses = 0
        meituanForegroundLaunchAttempts = 0
        meituanSearchResultAttempts = 0
        meituanDetailMatchAttempts = 0
        scheduleMeituan(1200)
    }

    private fun scheduleMeituan(delayMillis: Long) {
        if (!meituanNavigating || meituanRunScheduled) return
        meituanRunScheduled = true
        handler.postDelayed({
            meituanRunScheduled = false
            runMeituanStage()
        }, delayMillis)
    }

    private fun runMeituanStage() {
        if (!meituanNavigating) return
        val root = rootInActiveWindow ?: lastMeituanSource
        Log.d("HotelAutomation", "runMeituan stage=$meituanStage root=${describeNode(root)}")
        if (checkCaptchaPage("美团", root)) return
        if (root == null || root.packageName != "com.sankuai.meituan") {
            meituanForegroundMisses += 1
            if (meituanForegroundMisses >= 2 && meituanForegroundLaunchAttempts < 3) {
                meituanForegroundMisses = 0
                meituanForegroundLaunchAttempts += 1
                Log.d(
                    "HotelAutomation",
                    "Meituan is not foreground; relaunch attempt=$meituanForegroundLaunchAttempts",
                )
                launchMeituanApp()
                scheduleMeituan(1800)
            } else {
                scheduleMeituan(700)
            }
            return
        }
        meituanForegroundMisses = 0
        if (checkUpdatePopup(
                platform = "美团",
                root = root,
                allowInitialOcr = meituanStage == 0,
            )
        ) return
        when (meituanStage) {
            0 -> {
                val edit = findAcrossWindows(root) {
                    it.className?.toString() == "android.widget.EditText"
                }
                if (edit != null) {
                    meituanStage = 1
                } else {
                    val entry = findAcrossWindows(root) {
                        it.viewIdResourceName == "com.sankuai.meituan:id/search_layout_area"
                    }
                    if (entry != null) clickNodeOrParent(entry)
                    else {
                        val metrics = resources.displayMetrics
                        // On a returned search-results page the search field is at the
                        // very top. The old 14% fallback lands on the All/Places/Notes
                        // tabs and can switch to the map instead of opening search.
                        tapScreen(metrics.widthPixels * 0.5f, metrics.heightPixels * 0.075f)
                    }
                }
            }
            1 -> {
                val edit = findAcrossWindows(root) {
                    it.className?.toString() == "android.widget.EditText"
                }
                if (edit != null) {
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            meituanHotelName,
                        )
                    }
                    edit.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    if (edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) meituanStage = 2
                }
            }
            2 -> {
                val button = findAcrossWindows(root) {
                    it.viewIdResourceName == "com.sankuai.meituan:id/search_button"
                }
                if (button != null && clickNodeOrParent(button)) {
                    meituanSearchResultAttempts = 0
                    meituanStage = 3
                }
            }
            3 -> {
                meituanSearchResultAttempts += 1
                if (meituanSearchOcrInProgress) return
                if (meituanSearchOcrAttempts < 3 && ScreenCaptureService.isReady()) {
                    runMeituanSearchResultOcr()
                    return
                }
                val expected = normalize(meituanHotelName)
                val screenHeight = resources.displayMetrics.heightPixels
                val targetHotelCard = findFirst(root) { node ->
                    val bounds = Rect().also(node::getBoundsInScreen)
                    node.className?.toString() != "android.widget.EditText" &&
                        bounds.height() > 0 &&
                        bounds.top > screenHeight * 0.14f &&
                        normalize(nodeText(node)).contains(expected)
                }
                if (targetHotelCard != null && clickNodeOrParent(targetHotelCard)) {
                    meituanDetailMatchAttempts = 0
                    meituanStage = 4
                } else if (meituanSearchResultAttempts >= 15) {
                    skipCurrentMeituanTask("target hotel card not found in search results")
                }
            }
            4 -> {
                meituanDetailMatchAttempts += 1
                if (!isMeituanHotelDetail(root)) {
                    if (meituanDetailMatchAttempts >= 12) {
                        handleMeituanCandidateFailure(
                            "候选酒店未进入详情页",
                            returnFromDetail = false,
                        )
                    } else {
                        scheduleMeituan(700)
                    }
                    return
                }
                val expected = normalize(meituanHotelName)
                val matched = findFirst(root) {
                    normalize(nodeText(it)).contains(expected)
                } != null
                if (!matched) {
                    if (meituanDetailMatchAttempts >= 10) {
                        handleMeituanCandidateFailure(
                            "候选详情页酒店名与目标不一致",
                            returnFromDetail = true,
                        )
                    } else {
                        scheduleMeituan(700)
                    }
                    return
                }
                meituanStage = 6
                handler.postDelayed({
                    clickMeituanFilter()
                    handler.postDelayed({
                        // Re-find and click the semantic filter button to close the panel.
                        clickMeituanFilter()
                        handler.postDelayed({
                            meituanStage = 5
                            scheduleMeituan(100)
                        }, 4000)
                    }, 650)
                }, 1000)
            }
            5 -> {
                if (!meituanOcrInProgress) runMeituanOcr()
            }
        }
        scheduleMeituan(700)
    }

    private fun runMeituanSearchResultOcr() {
        meituanSearchOcrInProgress = true
        meituanSearchOcrAttempts += 1
        if (!ScreenCaptureService.recognize { lines ->
                if (!automationSessionActive) {
                    meituanSearchOcrInProgress = false
                    return@recognize
                }
                val exactCandidate = findOcrHotelCandidate(
                    rawLines = lines,
                    hotelName = meituanHotelName,
                    minimumTopFraction = 0.14f,
                )
                val allowFallbackCandidate =
                    meituanSearchRetryRound == 0 && meituanSearchOcrAttempts >= 3
                val candidate = exactCandidate ?: if (allowFallbackCandidate) {
                    findOcrHotelCandidate(
                        rawLines = lines,
                        hotelName = meituanHotelName,
                        minimumTopFraction = 0.14f,
                        allowAmbiguous = true,
                    )
                } else null
                val usingFallbackCandidate = exactCandidate == null && candidate != null
                Log.d(
                    "HotelAutomation",
                    "meituan search OCR hotel=$meituanHotelName candidate=$candidate " +
                        "fallback=$usingFallbackCandidate retryRound=$meituanSearchRetryRound " +
                        "attempt=$meituanSearchOcrAttempts",
                )
                meituanSearchOcrInProgress = false
                if (candidate != null) {
                    val tapped = openMeituanOcrMatchedCard(
                        candidate,
                        allowStatusShortcut = !usingFallbackCandidate,
                    )
                    if (!meituanNavigating) return@recognize
                    if (tapped) {
                        meituanDetailMatchAttempts = 0
                        meituanStage = 4
                        scheduleMeituan(1200)
                    } else if (meituanSearchOcrAttempts < 3) {
                        scheduleMeituan(800)
                    } else if (meituanSearchRetryRound == 0) {
                        restartMeituanSearchOnce("无法点击首个相似酒店")
                    } else {
                        completeMeituanStatus("review", "二次搜索仍无法点击目标酒店")
                    }
                } else if (meituanSearchOcrAttempts < 3) {
                    scheduleMeituan(900)
                } else if (meituanSearchRetryRound == 0) {
                    val tapped = openFirstMeituanSearchResult()
                    if (tapped) {
                        meituanDetailMatchAttempts = 0
                        meituanStage = 4
                        scheduleMeituan(1200)
                    } else {
                        restartMeituanSearchOnce("首次搜索无法定位相似酒店")
                    }
                } else {
                    completeMeituanStatus("review", "二次搜索仍无法准确识别目标酒店")
                }
            }) {
            meituanSearchOcrInProgress = false
            if (!automationSessionActive) return
            if (meituanSearchOcrAttempts < 3) scheduleMeituan(900)
            else if (meituanSearchRetryRound == 0) {
                restartMeituanSearchOnce("首次搜索页文字识别不可用")
            } else {
                completeMeituanStatus("review", "二次搜索页文字识别不可用")
            }
        }
    }

    private fun openMeituanOcrMatchedCard(
        candidate: OcrHotelCandidate,
        allowStatusShortcut: Boolean = true,
    ): Boolean {
        val root = rootInActiveWindow ?: lastMeituanSource
        val screenHeight = resources.displayMetrics.heightPixels
        val actionButtons = mutableListOf<Pair<AccessibilityNodeInfo, Rect>>()
        if (root != null) {
            walk(root) { node ->
                if (node.className?.toString() != "android.widget.Button") return@walk
                val text = normalize(nodeText(node))
                if (!text.matches(Regex("[3-5]\\.\\d")) &&
                    text !in setOf("满房", "歇业关闭", "暂停营业")
                ) return@walk
                val bounds = Rect().also(node::getBoundsInScreen)
                if (bounds.width() > 0 && bounds.height() > 0) {
                    actionButtons.add(node to bounds)
                }
            }
        }
        val matchedButton = actionButtons
            .asSequence()
            .filter { (_, bounds) ->
                kotlin.math.abs(bounds.centerY() - candidate.top) <= screenHeight * 0.20f
            }
            .minByOrNull { (_, bounds) ->
                kotlin.math.abs(bounds.centerY() - candidate.top)
            }
        if (matchedButton != null) {
            val buttonText = normalize(nodeText(matchedButton.first))
            if (allowStatusShortcut && buttonText == "满房") {
                completeMeituanStatus("full", "满房")
                return true
            }
            if (allowStatusShortcut && buttonText in setOf("歇业关闭", "暂停营业")) {
                completeMeituanStatus("error", buttonText)
                return true
            }
            Log.d(
                "HotelAutomation",
                "click matched Meituan card button=${nodeText(matchedButton.first)} " +
                    "bounds=${matchedButton.second} titleTop=${candidate.top}",
            )
            if (clickNodeOrParent(matchedButton.first)) return true
        }

        // Meituan always places the hotel image on the left side of a result card.
        // The image and OCR title share the same vertical card band, so this remains
        // reliable even when the title itself is drawn as non-clickable text.
        val metrics = resources.displayMetrics
        Log.d(
            "HotelAutomation",
            "fallback tap matched Meituan card image title=${candidate.text} top=${candidate.top}",
        )
        return tapScreen(metrics.widthPixels * 0.17f, candidate.top.toFloat())
    }

    private fun openFirstMeituanSearchResult(): Boolean {
        val root = rootInActiveWindow ?: lastMeituanSource
        val metrics = resources.displayMetrics
        val cardMarkers = mutableListOf<Rect>()
        if (root != null) {
            walk(root) { node ->
                if (node.className?.toString() != "android.widget.Button") return@walk
                val text = normalize(nodeText(node))
                if (!text.matches(Regex("[3-5]\\.\\d")) &&
                    text !in setOf("满房", "歇业关闭", "暂停营业")
                ) return@walk
                val bounds = Rect().also(node::getBoundsInScreen)
                if (bounds.height() > 0 && bounds.top > metrics.heightPixels * 0.14f) {
                    cardMarkers.add(bounds)
                }
            }
        }
        val first = cardMarkers.minByOrNull { it.top }
        val y = first?.centerY()?.toFloat() ?: (metrics.heightPixels * 0.30f)
        Log.d(
            "HotelAutomation",
            "open first similar Meituan result marker=$first retryRound=$meituanSearchRetryRound",
        )
        return tapScreen(metrics.widthPixels * 0.17f, y)
    }

    private fun restartMeituanSearchOnce(reason: String) {
        if (meituanSearchRetryRound >= 1) {
            completeMeituanStatus("review", "二次搜索仍无法确认目标酒店：$reason")
            return
        }
        Log.w(
            "HotelAutomation",
            "Meituan retry search once hotel=$meituanHotelName reason=$reason",
        )
        meituanSearchRetryRound = 1
        meituanNavigating = false
        meituanSearchOcrInProgress = false
        meituanOcrInProgress = false
        handler.postDelayed({
            if (automationSessionActive) {
                startMeituan(meituanHotelName, resetSearchRetry = false)
            }
        }, 900)
    }

    private fun handleMeituanCandidateFailure(
        reason: String,
        returnFromDetail: Boolean,
    ) {
        meituanNavigating = false
        meituanSearchOcrInProgress = false
        meituanOcrInProgress = false
        if (returnFromDetail) performGlobalAction(GLOBAL_ACTION_BACK)
        if (meituanSearchRetryRound == 0) {
            meituanSearchRetryRound = 1
            Log.w(
                "HotelAutomation",
                "Meituan candidate rejected; return and search once hotel=$meituanHotelName " +
                    "reason=$reason",
            )
            handler.postDelayed({
                if (automationSessionActive) {
                    startMeituan(meituanHotelName, resetSearchRetry = false)
                }
            }, if (returnFromDetail) 1400 else 900)
        } else {
            Log.w(
                "HotelAutomation",
                "Meituan second candidate rejected; keep search screenshot hotel=$meituanHotelName " +
                    "reason=$reason",
            )
            handler.postDelayed({
                if (automationSessionActive) {
                    completeMeituanStatus("review", "二次核验失败：$reason")
                }
            }, if (returnFromDetail) 1400 else 300)
        }
    }

    private fun findOcrHotelCandidate(
        rawLines: List<Map<String, Any>>,
        hotelName: String,
        minimumTopFraction: Float,
        allowAmbiguous: Boolean = false,
    ): OcrHotelCandidate? {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val target = normalizeHotelForMatch(hotelName)
        if (target.isBlank()) return null
        val lines = rawLines.mapNotNull { item ->
            val text = item["text"]?.toString()?.trim().orEmpty()
            val left = item["left"] as? Int ?: return@mapNotNull null
            val top = item["top"] as? Int ?: return@mapNotNull null
            val right = item["right"] as? Int ?: return@mapNotNull null
            val bottom = item["bottom"] as? Int ?: return@mapNotNull null
            if (text.isBlank() || right <= left || bottom <= top) null
            else OcrLine(text, left, top, right, bottom)
        }.filter { it.top > screenHeight * minimumTopFraction }
        val starts = lines.filter { line ->
            val normalized = normalizeHotelForMatch(line.text)
            !line.text.contains('¥') && !line.text.contains('￥') &&
                (containsHotelWord(normalized) || hotelNameSimilarity(target, normalized) >= 0.58)
        }
        val candidates = starts.flatMap { first ->
            val following = lines
                .asSequence()
                .filter { next ->
                    next.top >= first.bottom - 6 &&
                        next.top - first.bottom <= screenHeight * 0.07f &&
                        kotlin.math.abs(next.left - first.left) <= screenWidth * 0.28f &&
                        !next.text.contains('¥') && !next.text.contains('￥')
                }
                .sortedBy { it.top }
                .take(2)
                .toList()
            buildList {
                add(listOf(first))
                if (following.isNotEmpty()) add(listOf(first, following[0]))
                if (following.size > 1) add(listOf(first, following[0], following[1]))
            }.map { parts ->
                val text = parts.joinToString("") { it.text }
                OcrHotelCandidate(
                    text = text,
                    left = parts.minOf { it.left },
                    top = parts.minOf { it.top },
                    right = parts.maxOf { it.right },
                    bottom = parts.maxOf { it.bottom },
                    score = hotelNameSimilarity(target, normalizeHotelForMatch(text)),
                )
            }
        }.sortedByDescending { it.score }
        val best = candidates.firstOrNull() ?: return null
        val runnerUp = candidates.firstOrNull { other ->
            kotlin.math.abs(other.top - best.top) > screenHeight * 0.09f
        }
        val unique = best.score >= 0.70 &&
            (best.score >= 0.95 || runnerUp == null || best.score - runnerUp.score >= 0.12)
        Log.d(
            "HotelAutomation",
            "OCR hotel candidate target=$hotelName best=${best.text}:${best.score} " +
                "runner=${runnerUp?.text}:${runnerUp?.score} unique=$unique",
        )
        return best.takeIf { unique || (allowAmbiguous && best.score >= 0.40) }
    }

    private fun skipCurrentMeituanTask(reason: String) {
        Log.w(
            "HotelAutomation",
            "Skip Meituan hotel=$meituanHotelName reason=$reason",
        )
        completeMeituanStatus("error", reason)
    }

    private fun completeMeituanStatus(status: String, reason: String) {
        val hotelName = currentMeituanBatchTask?.hotelName ?: meituanHotelName
        meituanNavigating = false
        meituanOcrInProgress = true
        meituanSearchOcrInProgress = false
        fun persistAndFinish(screenshotPath: String?) {
            val data = JSONObject()
                .put("hotelName", hotelName)
                .put("status", status)
                .put("capturedAt", System.currentTimeMillis())
                .put("reason", reason)
            if (!screenshotPath.isNullOrBlank()) data.put("screenshotPath", screenshotPath)
            currentMeituanBatchTask?.let { data.put("taskId", it.taskId) }
            persistMeituanCapture(data)
            Log.d(
                "HotelAutomation",
                "Meituan status hotel=$hotelName status=$status reason=$reason " +
                    "screenshot=$screenshotPath",
            )
            meituanOcrInProgress = false
            finishMeituanCapture()
        }
        val started = ScreenCaptureService.captureScreenshot("meituan_$status") { screenshotPath ->
            if (!automationSessionActive) return@captureScreenshot
            persistAndFinish(screenshotPath)
        }
        if (!started) persistAndFinish(null)
    }

    private fun persistMeituanCapture(data: JSONObject) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(LAST_MEITUAN_CAPTURE, data.toString()).apply()
        if (currentMeituanBatchTask != null) {
            val captures = JSONArray(prefs.getString(BATCH_MEITUAN_CAPTURES, "[]"))
            captures.put(data)
            prefs.edit().putString(BATCH_MEITUAN_CAPTURES, captures.toString()).apply()
        }
    }

    private fun clickMeituanFilter(): Boolean {
        val currentRoot = rootInActiveWindow ?: lastMeituanSource
        val filter = findAcrossWindows(currentRoot) {
            normalize(nodeText(it)) == "筛选"
        } ?: return false
        return clickNodeOrParent(filter)
    }

    private fun runMeituanOcr() {
        meituanOcrInProgress = true
        meituanOcrAttempts += 1
        if (!ScreenCaptureService.captureScreenshot("meituan") { screenshotPath ->
                if (!automationSessionActive) {
                    meituanOcrInProgress = false
                    return@captureScreenshot
                }
                if (screenshotPath != null) {
                    val data = JSONObject().put("hotelName", meituanHotelName)
                        .put("screenshotPath", screenshotPath)
                        .put("status", "success")
                        .put("capturedAt", System.currentTimeMillis())
                    currentMeituanBatchTask?.let { data.put("taskId", it.taskId) }
                    persistMeituanCapture(data)
                    meituanNavigating = false
                    meituanOcrInProgress = false
                    finishMeituanCapture()
                } else {
                    meituanOcrInProgress = false
                    if (meituanOcrAttempts < 3) {
                        scheduleMeituan(900)
                    } else {
                        completeMeituanStatus("error", "截图保存失败")
                    }
                }
            }) {
            meituanOcrInProgress = false
            if (!automationSessionActive) return
            if (meituanOcrAttempts < 3) scheduleMeituan(900)
            else {
                completeMeituanStatus("error", "截图服务不可用")
            }
        }
    }

    private fun startMeituanBatchQueue(
        tasks: List<Map<String, String>>,
        firstTaskDelayMillis: Long = 800,
        resetCaptures: Boolean = true,
    ) {
        meituanBatchQueue.clear()
        tasks.mapNotNullTo(meituanBatchQueue) { task ->
            val id = task["taskId"].orEmpty()
            val hotelName = task["hotelName"].orEmpty()
            if (id.isBlank() || hotelName.isBlank()) null
            else BatchTask(id, task["city"].orEmpty(), hotelName)
        }
        if (resetCaptures) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(BATCH_MEITUAN_CAPTURES, "[]").apply()
        }
        currentMeituanBatchTask = null
        startNextMeituanBatchTask(true, firstTaskDelayMillis)
    }

    private fun finishMeituanCapture() {
        if (currentMeituanBatchTask == null) {
            combinedRunActive = false
            playCompletionSound()
            returnToMonitorApp()
        } else if (meituanBatchQueue.isNotEmpty()) {
            // Leave the completed hotel's detail page before searching the next hotel.
            // Relaunching Meituan here can preserve the old detail activity and make
            // stage 0 tap an unrelated detail tab instead of the search entry.
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({ startNextMeituanBatchTask(false) }, 1400)
        } else {
            startNextMeituanBatchTask(false)
        }
    }

    private fun startNextMeituanBatchTask(first: Boolean, firstTaskDelayMillis: Long = 800) {
        val next = meituanBatchQueue.removeFirstOrNull()
        if (next == null) {
            currentMeituanBatchTask = null
            combinedRunActive = false
            playCompletionSound()
            returnToMonitorApp()
            return
        }
        currentMeituanBatchTask = next
        handler.postDelayed(
            {
                if (first) prepareFirstMeituanSearch(next.hotelName)
                else startMeituan(next.hotelName)
            },
            if (first) firstTaskDelayMillis else 500,
        )
    }

    private fun prepareFirstMeituanSearch(hotelName: String) {
        verifyMeituanSearchEntry(hotelName, 0)
    }

    private fun verifyMeituanSearchEntry(hotelName: String, attempt: Int) {
        if (attempt >= 6) {
            Log.w("HotelAutomation", "Unable to normalize Meituan entry page; skip $hotelName")
            skipCurrentMeituanTask("unable to reach Meituan search page")
            return
        }
        val root = rootInActiveWindow
        val packageName = root?.packageName?.toString().orEmpty()
        if (packageName == "com.sankuai.meituan") {
            if (isMeituanHotelDetail(root)) {
                Log.d(
                    "HotelAutomation",
                    "Meituan restored detail class=$lastMeituanWindowClass; press Back attempt=$attempt",
                )
                performGlobalAction(GLOBAL_ACTION_BACK)
                handler.postDelayed({ verifyMeituanSearchEntry(hotelName, attempt + 1) }, 1500)
                return
            }
            if (isMeituanSearchReady(root)) {
                Log.d(
                    "HotelAutomation",
                    "Meituan search entry ready class=$lastMeituanWindowClass attempt=$attempt",
                )
                startMeituan(hotelName)
                return
            }
            // A Meituan home/loading page is safe for stage 0; it will locate or
            // tap the search entry after the page has fully settled.
            handler.postDelayed({
                val settledRoot = rootInActiveWindow
                if (settledRoot?.packageName == "com.sankuai.meituan" &&
                    isMeituanHotelDetail(settledRoot)
                ) {
                    verifyMeituanSearchEntry(hotelName, attempt + 1)
                } else {
                    startMeituan(hotelName)
                }
            }, 1000)
            return
        }

        val isLauncher = packageName == "com.miui.home" ||
            packageName.contains("launcher", ignoreCase = true)
        Log.d(
            "HotelAutomation",
            "Meituan entry package=$packageName launcher=$isLauncher; reopen from desktop attempt=$attempt",
        )
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            clickLauncherAppIcon("美团", 0.62f, 0.655f)
            handler.postDelayed({ verifyMeituanSearchEntry(hotelName, attempt + 1) }, 1800)
        }, if (isLauncher) 600 else 1200)
    }

    private fun isMeituanSearchReady(root: AccessibilityNodeInfo): Boolean {
        if (lastMeituanWindowClass.contains("SearchResultActivity", ignoreCase = true)) return true
        return findAcrossWindows(root) {
            it.className?.toString() == "android.widget.EditText" ||
                it.viewIdResourceName == "com.sankuai.meituan:id/search_layout_area"
        } != null
    }

    private fun isMeituanHotelDetail(root: AccessibilityNodeInfo): Boolean {
        val windowClass = lastMeituanWindowClass
        if (windowClass.contains("HotelPoiDetailActivity", ignoreCase = true) ||
            (windowClass.contains("hotel", ignoreCase = true) &&
                windowClass.contains("detail", ignoreCase = true))
        ) return true
        val markers = setOf("预订", "筛选", "评价", "设施", "周边")
        var markerCount = 0
        walk(root) { node ->
            if (normalize(nodeText(node)) in markers) markerCount += 1
        }
        return markerCount >= 2
    }

    private fun launchMeituanApp() {
        meituanUpdatePopupGuard.initialOcrCompleted = false
        val launch = packageManager.getLaunchIntentForPackage("com.sankuai.meituan")
        launch?.addFlags(
            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
        if (launch != null) startActivity(launch)
    }

    private fun normalizeRoomName(value: String) = value.trim().replace(Regex("\\s+"), "")

    private fun scheduleOrStop() {
        if (!navigating || ctripOcrInProgress) return
        if (attempts < 50) {
            scheduleSearch(700)
        } else if (currentBatchTask != null) {
            Log.w(
                "HotelAutomation",
                "Ctrip task timed out without an exact hotel price; skip and continue: " +
                    currentBatchTask?.hotelName,
            )
            saveCtripErrorResult(
                currentBatchTask?.hotelName.orEmpty(),
                "未识别到价格",
            )
            startNextBatchTask(first = false)
        } else {
            stopNavigation()
        }
    }

    private fun moveTo(nextStage: Stage) {
        stage = nextStage
        attempts = 0
        searchTextEntered = false
    }

    private fun startPreparation(city: String, hotelName: String) {
        targetCity = city
        targetHotel = hotelName
        adultIncremented = false
        collectedRooms.clear()
        scrollAttempts = 0
        resultInProgress = false
        ctripOcrInProgress = false
        ctripOcrAttempts = 0
        ctripHotelCategoryOpened = false
        typedLength = 0
        stage = if (ctripHomeResetRequired) Stage.RESET_TO_HOME else Stage.FIND_GLOBAL_SEARCH
        startNavigation()
    }

    private fun startHotelOnlyNavigation() {
        targetCity = null
        targetHotel = null
        stage = Stage.FIND_GLOBAL_SEARCH
        startNavigation()
    }

    private fun findByExactText(root: AccessibilityNodeInfo, value: String) =
        findFirst(root) { nodeText(it) == value }

    private fun findByText(root: AccessibilityNodeInfo, value: String) =
        findFirst(root) { nodeText(it).contains(value) }

    private fun findByNormalizedText(root: AccessibilityNodeInfo, value: String, excludeEditText: Boolean = false) =
        findFirst(root) {
            (!excludeEditText || it.className?.toString() != "android.widget.EditText") &&
                normalize(nodeText(it)) == normalize(value)
        }

    private fun normalize(value: String) = value
        .replace('\uFF08', '(')
        .replace('\uFF09', ')')
        .replace(Regex("\\s+"), "")

    private fun nodeText(node: AccessibilityNodeInfo): String =
        node.text?.toString().orEmpty().ifEmpty { node.contentDescription?.toString().orEmpty() }

    private fun walk(node: AccessibilityNodeInfo, action: (AccessibilityNodeInfo) -> Unit) {
        action(node)
        for (index in 0 until node.childCount) node.getChild(index)?.let { walk(it, action) }
    }

    private fun findFirst(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findFirst(child, predicate)?.let { return it }
        }
        return null
    }

    private fun clickNodeOrParent(startNode: AccessibilityNodeInfo): Boolean {
        var node: AccessibilityNodeInfo? = startNode
        repeat(10) {
            if (node?.isClickable == true) return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            node = node?.parent
        }
        return false
    }

    private fun stopNavigation() {
        navigating = false
        handler.removeCallbacksAndMessages(null)
        lastCtripSource = null
    }
}
