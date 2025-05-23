package com.mtc.mtcai.modules.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityNodeInfo
import com.mtc.mtcai.R

class MyAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private var lastTapTime = 0L
    private var lastTappedNode: AccessibilityNodeInfo? = null
    private val DOUBLE_TAP_THRESHOLD = 300L

    override fun onServiceConnected() {
        super.onServiceConnected()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val currentLocale = resources.configuration.locales[0]
                tts.language = currentLocale
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val source = event.source ?: return
            val currentTime = System.currentTimeMillis()

            val description = getNodeDescription(source)

            if (lastTappedNode != null && currentTime - lastTapTime < DOUBLE_TAP_THRESHOLD &&
                source.equals(lastTappedNode)) {
                performDoubleTapAction(source)
            } else {
                tts.speak(description, TextToSpeech.QUEUE_FLUSH, null, null)
                lastTappedNode = source
                lastTapTime = currentTime
            }
        }
    }

    override fun onInterrupt() {}

    override fun onInit(status: Int) {
        val currentLocale = resources.configuration.locales[0]
        if (status == TextToSpeech.SUCCESS) {
            tts.language = currentLocale
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    private fun getNodeDescription(node: AccessibilityNodeInfo): String {
        return when {
            !node.contentDescription.isNullOrEmpty() -> node.contentDescription.toString()
            !node.text.isNullOrEmpty() -> node.text.toString()
            node.className?.contains("Image") == true -> ""
            else -> getString(R.string.unknown_object)
        }
    }

    private fun performDoubleTapAction(node: AccessibilityNodeInfo) {
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            val x = rect.centerX().toFloat()
            val y = rect.centerY().toFloat()

            val path = Path().apply {
                moveTo(x, y)
                lineTo(x, y)
            }

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 1L))
                .addStroke(GestureDescription.StrokeDescription(path, 100L, 1L))
                .build()

            dispatchGesture(gesture, null, null)
        }
    }
}
