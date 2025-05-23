package com.mtc.mtcai.modules.overlay

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.animation.Animation
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.mtc.mtcai.R
import com.mtc.mtcai.core.device.FlashlightController
import com.mtc.mtcai.core.tts.TtsManager
import com.mtc.mtcai.core.util.NetworkUtils
import com.mtc.mtcai.data.model.VoiceAssistantResponse
import com.mtc.mtcai.modules.handler.VoiceAssistantHandler
import com.mtc.mtcai.modules.morze.convertToMorse
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : Service() {

    @Inject
    lateinit var flashlightController: FlashlightController

    @Inject
    lateinit var voiceHandler: VoiceAssistantHandler

    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val devicePolicyManager by lazy { getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    private val adminComponent by lazy { ComponentName(this, DeviceAdminReceiver::class.java) }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: LinearLayout
    private lateinit var microphoneButton: ImageButton
    private lateinit var textView: TextView
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var params: WindowManager.LayoutParams
    private var isListening = false
    private var initialY = 300
    private var initialTouchY = 0f

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var isFaceDown = false
    private var lastText = ""
    @Volatile
    private var isMorsePlaying = false

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val z = it.values[2]
                    if (z < -8.0 && !isFaceDown) {
                        isFaceDown = true
                        TtsManager.stop()

                        val morse = convertToMorse(lastText)

                        MainScope().launch {
                            playMorseViaSound(morse)
                        }

                    } else if (z > -8.0 && isFaceDown) {
                        isFaceDown = false
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    suspend fun playMorseViaSound(morse: String) {
        isMorsePlaying = true
        val toneGen = android.media.ToneGenerator(AudioManager.STREAM_MUSIC, 100)

        for (symbol in morse) {
            if (!isMorsePlaying) break // Остановить, если выключено

            when (symbol) {
                '.' -> {
                    toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 100)
                    delay(200)
                }
                '-' -> {
                    toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 300)
                    delay(400)
                }
                ' ' -> delay(500)
            }
        }

        toneGen.release()
        isMorsePlaying = false
    }

    fun stopMorseSound() {
        isMorsePlaying = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()

        TtsManager.init(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)


        setupRecognizer()
        createOverlayView()

        // Регистрация слушателя сенсора Акселерометр
        accelerometer?.let {
            sensorManager.registerListener(
                sensorListener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        } ?: run {}
    }

    private fun setupRecognizer() {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {

            override fun onResults(results: Bundle?) {
                isListening = false
                microphoneButton.background = createButtonBackground(false)
                TtsManager.stop()
                val text =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    handleVoiceCommand(text)
                }
                stopMicAnimation()
            }

            override fun onError(error: Int) {
                isListening = false
                microphoneButton.background = createButtonBackground(false)
                TtsManager.stop()
                textView.text = getString(R.string.recognition_error)
                TtsManager.speak(getString(R.string.recognition_error))
                stopMicAnimation()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                textView.text = getString(R.string.loading)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    @SuppressLint("ClickableViewAccessibility", "UseKtx")
    private fun createOverlayView() {
        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#80000000"))

            textView = TextView(this@OverlayService).apply {
                text = getString(R.string.press_and_speak)
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }
            addView(textView)

            microphoneButton = ImageButton(this@OverlayService).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.mic_height)
                ).apply {
                    marginStart = 30
                    marginEnd = 30
                }
                setImageDrawable(
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_mic_state,
                        null
                    )
                )
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                adjustViewBounds = true
                background = createButtonBackground(false)
                setPadding(10, 20, 10, 20)
                setOnClickListener {
                    if (!isListening) checkMicrophonePermission() else stopListening()
                }
            }
            addView(microphoneButton)

            val touchListener = View.OnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_MOVE -> {
                        if (isLongPress(event)) {
                            alpha = 0.6f
                            updateButtonPosition(event)
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        alpha = 1.0f
                        initialTouchY = event.rawY
                        initialY = params.y
                    }

                    MotionEvent.ACTION_DOWN -> {
                        alpha = 1.0f
                        initialTouchY = event.rawY
                        initialY = params.y
                    }
                }
                false
            }

            microphoneButton.setOnTouchListener(touchListener)
            setOnTouchListener(touchListener)
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            x = 0
            y = initialY
        }

        windowManager.addView(overlayView, params)
    }

    private fun isLongPress(event: MotionEvent) = event.eventTime - event.downTime > 500

    private fun updateButtonPosition(event: MotionEvent) {
        val maxY = resources.displayMetrics.heightPixels - 200
        val deltaY = (initialTouchY - event.rawY).toInt()
        params.y = (initialY + deltaY).coerceIn(0, maxY)
        windowManager.updateViewLayout(overlayView, params)
    }

    @SuppressLint("UseKtx")
    private fun createButtonBackground(isActive: Boolean): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 30f
        setColor(if (isActive) Color.parseColor("#4CAF50") else Color.parseColor("#FF6200EE"))
        setStroke(2, Color.WHITE)
    }

    private fun startMicAnimation() {
        val anim = AnimationUtils.loadAnimation(this, R.anim.mic_pulse).apply {
            repeatCount = Animation.INFINITE
        }
        microphoneButton.startAnimation(anim)
        (microphoneButton.drawable as? AnimatedVectorDrawable)?.start()
    }

    private fun stopMicAnimation() {
        microphoneButton.clearAnimation()
        (microphoneButton.drawable as? AnimatedVectorDrawable)?.stop()
    }

    private fun checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceRecognition()
        } else {
            Toast.makeText(this, getString(R.string.no_microphone_permission), Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun startVoiceRecognition() {
        val currentLocale = resources.configuration.locales[0].language

        Log.e("log lang", currentLocale)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        }
        stopMorseSound()
        TtsManager.stop()
        isListening = true
        textView.text = getString(R.string.listening)
        microphoneButton.background = createButtonBackground(true)
        speechRecognizer.startListening(intent)
        startMicAnimation()
    }

    private fun stopListening() {
        speechRecognizer.stopListening()
        isListening = false
        microphoneButton.background = createButtonBackground(false)
    }

    @SuppressLint("SetTextI18n")
    private fun handleVoiceCommand(text: String) {
        Log.d("log", text)

        if (text == getString(R.string.shutdown_command)) {
            stopSelf()
            return
        }

        MainScope().launch {
            try {
                if (!NetworkUtils.isInternetAvailable(applicationContext)) {
                    executeActionOff(text)
                } else {
                    val result = voiceHandler.handle(text)
                    lastText = result.response
                    textView.text = result.response
                    TtsManager.speak(result.response)
                    executeAction(result.command, result)
                    Log.e(
                        "log",
                        "response= ${result.response} command= ${result.command} params= ${result.params}"
                    )
                }
            } catch (e: Exception) {
                textView.text = getString(R.string.error_occurred)
                TtsManager.speak(getString(R.string.error_occurred))
            }
        }
    }

    @SuppressLint("UseKtx")
    private fun executeActionOff(command: String) {
        Log.e("log", "executeActionOff")
        when (command.lowercase()) {
            getString(R.string.hello) -> {
                textView.text = getString(R.string.hello_response)
                TtsManager.speak(getString(R.string.hello_response))
            }

            getString(R.string.turn_on_flashlight) -> flashlightController.toggleFlashlight(true)
            getString(R.string.turn_off_flashlight) -> flashlightController.toggleFlashlight(false)

            getString(R.string.open_telegram) -> {
                val launchIntent =
                    packageManager.getLaunchIntentForPackage("org.telegram.messenger")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                } else {
                    textView.text = getString(R.string.telegram_not_installed)
                    TtsManager.speak(getString(R.string.telegram_not_installed))
                }
            }

            getString(R.string.open_whatsapp) -> {
                val launchIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                } else {
                    textView.text = getString(R.string.whatsapp_not_installed)
                    TtsManager.speak(getString(R.string.whatsapp_not_installed))
                }
            }

            getString(R.string.open_phone) -> startActivity(
                Intent(Intent.ACTION_DIAL).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            )

            getString(R.string.open_camera) -> {
                startActivity(
                    Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                )
            }

            getString(R.string.open_contacts) -> {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        ContactsContract.Contacts.CONTENT_URI
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }

            getString(R.string.open_gallery) -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        type = "image/*"
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    textView.text = getString(R.string.gallery_open_failed)
                    TtsManager.speak(getString(R.string.gallery_open_failed))
                }
            }


            else -> {
                textView.text = getString(R.string.no_internet)
                TtsManager.speak(getString(R.string.no_internet))
            }
        }
    }

    @SuppressLint("UseKtx")
    private fun executeAction(command: String, result: VoiceAssistantResponse) {
        when (command) {
            "answer_question" -> {}
            "unknown" -> {}
            "error" -> {}

            "turn_on_light" -> flashlightController.toggleFlashlight(true)
            "turn_off_light" -> flashlightController.toggleFlashlight(false)
            "open_contacts" -> startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    ContactsContract.Contacts.CONTENT_URI
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )

            "open_phone" -> startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

            "turn_on_camera" -> startActivity(
                Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            )

            "open_telegram" -> {
                val launchIntent =
                    packageManager.getLaunchIntentForPackage("org.telegram.messenger")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                } else {
                    textView.text = getString(R.string.telegram_not_installed)
                    TtsManager.speak(getString(R.string.telegram_not_installed))
                }
            }

            "open_whatsapp" -> {
                val launchIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                } else {
                    textView.text = getString(R.string.whatsapp_not_installed)
                    TtsManager.speak(getString(R.string.whatsapp_not_installed))
                }
            }

            "open_maps" -> {
                val launchIntent =
                    packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                try {
                    startActivity(launchIntent)
                } catch (e: Exception) {
                    textView.text = getString(R.string.maps_not_found)
                    TtsManager.speak(getString(R.string.maps_not_found))
                }
            }

            "open_browser" -> {
                val launchIntent =
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                try {
                    startActivity(launchIntent)
                } catch (e: Exception) {
                    textView.text = getString(R.string.browser_not_found)
                    TtsManager.speak(getString(R.string.browser_not_found))
                }
            }

            "turn_off_sound" -> {
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                    textView.text = getString(R.string.sound_off)
                    TtsManager.speak(getString(R.string.sound_off))
                } catch (e: Exception) {
                    textView.text = getString(R.string.sound_error)
                    TtsManager.speak(getString(R.string.sound_error))
                }
            }

            "turn_on_sound" -> {
                try {
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2,
                        0
                    )
                    textView.text = getString(R.string.sound_on)
                    TtsManager.speak(getString(R.string.sound_on))
                } catch (e: Exception) {
                    textView.text = getString(R.string.sound_error)
                    TtsManager.speak(getString(R.string.sound_error))
                }
            }

            "turn_on_bluetooth" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val permission = Manifest.permission.BLUETOOTH_CONNECT
                    if (ContextCompat.checkSelfPermission(
                            this,
                            permission
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        textView.text = getString(R.string.bluetooth_permission_required)
                        TtsManager.speak(getString(R.string.bluetooth_permission_required))
                        return
                    }
                }

                try {
                    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    textView.text = getString(R.string.bluetooth_activation_failed)
                    TtsManager.speak(getString(R.string.bluetooth_activation_failed))
                }
            }


            "turn_off_bluetooth" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val permission = Manifest.permission.BLUETOOTH_CONNECT
                    if (ContextCompat.checkSelfPermission(
                            this,
                            permission
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        textView.text = getString(R.string.bluetooth_permission_required)
                        TtsManager.speak(getString(R.string.bluetooth_permission_required))
                        return
                    }
                }

                try {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    textView.text = getString(R.string.please_turn_off_bluetooth)
                    TtsManager.speak(getString(R.string.please_turn_off_bluetooth))
                } catch (e: Exception) {
                    textView.text = getString(R.string.bluetooth_settings_open_failed)
                    TtsManager.speak(getString(R.string.bluetooth_settings_open_failed))
                }
            }

            "play_music" -> {
                val launchIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(launchIntent)
                } catch (e: Exception) {
                    textView.text = getString(R.string.player_not_found)
                    TtsManager.speak(getString(R.string.player_not_found))
                }
            }

            "open_calendar" -> {
                val calendarIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("content://com.android.calendar/time")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val knownCalendarPackages = listOf(
                    "com.android.calendar",
                    "com.google.android.calendar",
                    "com.xiaomi.calendar",
                    "com.miui.notes"
                )

                try {
                    val resolved = knownCalendarPackages.firstOrNull { pkg ->
                        calendarIntent.setPackage(pkg)
                        calendarIntent.resolveActivity(packageManager) != null
                    }

                    if (resolved != null) {
                        calendarIntent.setPackage(resolved)
                        startActivity(calendarIntent)
                    } else {
                        calendarIntent.setPackage(null)
                        startActivity(calendarIntent)
                    }
                } catch (e: Exception) {
                    textView.text = getString(R.string.calendar_not_found)
                    TtsManager.speak(getString(R.string.install_calendar))
                }
            }

            "open_note" -> {
                val launchIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = CalendarContract.CONTENT_URI
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(launchIntent)
                } catch (e: Exception) {
                    textView.text = getString(R.string.note_not_found)
                    TtsManager.speak(getString(R.string.note_not_found))
                }
            }

            "open_notes" -> {
                val launchIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = CalendarContract.CONTENT_URI
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(launchIntent)
                } catch (e: Exception) {
                    textView.text = getString(R.string.note_not_found)
                    TtsManager.speak(getString(R.string.note_not_found))
                }
            }

            "open_settings" -> {
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    textView.text = getString(R.string.settings_open_failed)
                    TtsManager.speak(getString(R.string.settings_open_failed))
                }
            }

            "open_gallery" -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        type = "image/*"
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    textView.text = getString(R.string.gallery_open_failed)
                    TtsManager.speak(getString(R.string.gallery_open_failed))
                }
            }

            "open_voice_recorder" -> {
                val recorderPackages = listOf(
                    "com.android.soundrecorder",
                    "com.sec.android.app.voicenote",
                    "com.miui.voicerecorder"
                )
                val intent = recorderPackages
                    .mapNotNull { packageManager.getLaunchIntentForPackage(it) }
                    .firstOrNull()

                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } else {
                    textView.text = getString(R.string.recorder_not_found)
                    TtsManager.speak(getString(R.string.recorder_not_found))
                }
            }

            "start_recording" -> {
                textView.text = getString(R.string.manual_recorder_start)
                TtsManager.speak(getString(R.string.manual_recorder_start))
            }

            "stop_recording" -> {
                textView.text = getString(R.string.manual_recorder_stop)
                TtsManager.speak(getString(R.string.manual_recorder_stop))
            }

            "open_clock" -> {
                val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(clockIntent)
                } catch (e: Exception) {
                    textView.text = getString(R.string.clock_not_found)
                    TtsManager.speak(getString(R.string.clock_not_found))
                }
            }

            "create_event" -> {
                val launchIntent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(launchIntent)
                } catch (e: Exception) {
                    textView.text = getString(R.string.event_creation_error)
                    TtsManager.speak(getString(R.string.event_creation_error))
                }
            }

            "activate_voice_search" -> {
                val launchIntent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(launchIntent)
                } catch (e: Exception) {
                    textView.text = getString(R.string.voice_search_not_available)
                    TtsManager.speak(getString(R.string.voice_search_not_available))
                }
            }

            "turn_off_phone" -> {
                try {
                    if (devicePolicyManager.isDeviceOwnerApp(packageName) || devicePolicyManager.isAdminActive(
                            adminComponent
                        )
                    ) {
                        devicePolicyManager.lockNow()
                        textView.text = getString(R.string.screen_locked)
                        TtsManager.speak(getString(R.string.screen_locked))
                    } else {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                        textView.text = getString(R.string.admin_rights_required)
                        TtsManager.speak(getString(R.string.admin_rights_required))
                    }
                } catch (e: Exception) {
                    textView.text = getString(R.string.admin_rights_required)
                    TtsManager.speak(getString(R.string.admin_rights_required))
                }
            }

            "lock_screen" -> {
                try {
                    if (devicePolicyManager.isDeviceOwnerApp(packageName) || devicePolicyManager.isAdminActive(
                            adminComponent
                        )
                    ) {
                        devicePolicyManager.lockNow()
                        textView.text = getString(R.string.screen_locked)
                        TtsManager.speak(getString(R.string.screen_locked))
                    } else {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                        textView.text = getString(R.string.admin_rights_required1)
                        TtsManager.speak(getString(R.string.admin_rights_required1))
                    }
                } catch (e: Exception) {
                    Log.e("LockScreen", "Ошибка: ${e.message}")
                    textView.text = getString(R.string.screen_lock_failed)
                    TtsManager.speak("${getString(R.string.screen_lock_failed)}: ${e.localizedMessage}")
                }
            }


            "turn_off_app" -> stopSelf()
            "search_internet" -> {}

            "send_message" -> {
                Log.e(
                    "log",
                    "response= ${result.response} command= ${result.command} params= ${result.params}"
                )
                if (result.params.isEmpty()) return
            }

            "call_contact" -> {
                try {
                    if (result.params.isEmpty()) return
                    val contactName = result.params["contact"] ?: run {
                        textView.text = getString(R.string.contact_not_specified)
                        TtsManager.speak(getString(R.string.contact_not_specified))
                        return@executeAction
                    }

                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.CALL_PHONE
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        textView.text = getString(R.string.call_permission_required)
                        TtsManager.speak(getString(R.string.call_permission_required))
                        return@executeAction
                    }

                    val contactUri = Uri.withAppendedPath(
                        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(contactName)
                    )

                    val projection = arrayOf(ContactsContract.PhoneLookup.NUMBER)
                    val cursor = contentResolver.query(
                        contactUri,
                        projection,
                        null,
                        null,
                        null
                    )

                    cursor?.use {
                        if (it.moveToFirst()) {
                            val phoneNumber = it.getString(0)
                            val callIntent = Intent(Intent.ACTION_CALL).apply {
                                data = Uri.parse("tel:$phoneNumber")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(callIntent)
                            textView.text = getString(R.string.calling_contact, contactName)
                            TtsManager.speak(getString(R.string.calling_contact, contactName))
                        } else {
                            textView.text = getString(R.string.contact_not_found, contactName)
                            TtsManager.speak(getString(R.string.contact_not_found, contactName))
                        }
                    } ?: run {
                        textView.text = getString(R.string.contacts_error)
                        TtsManager.speak(getString(R.string.contacts_error))
                    }

                } catch (e: Exception) {
                    Log.e("CallContact", "Error: ${e.message}")
                    textView.text = getString(R.string.call_failed)
                    TtsManager.speak(getString(R.string.call_failed))
                }
            }

            "set_alarm" -> {
                try {
                    if (result.params.isEmpty()) {
                        textView.text = getString(R.string.alarm_time_not_specified)
                        TtsManager.speak(getString(R.string.alarm_time_not_specified))
                        return@executeAction
                    }

                    val time = result.params["time"] ?: run {
                        textView.text = getString(R.string.alarm_time_not_specified)
                        TtsManager.speak(getString(R.string.alarm_time_not_specified))
                        return@executeAction
                    }

                    val date = result.params["date"] ?: run {
                        textView.text = getString(R.string.alarm_time_not_specified)
                        TtsManager.speak(getString(R.string.alarm_time_not_specified))
                        return@executeAction
                    }

                    val (hour, minute) = time.split(":").map { it.toInt() }
                    val (day, month) = date.split(".").map { it.toInt() }

                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_MONTH, day)
                        set(Calendar.MONTH, month - 1)
                        set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
                    }
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

                    val days = listOf(dayOfWeek)

                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, getString(R.string.default_alarm_name))
                        putExtra(AlarmClock.EXTRA_DAYS, ArrayList<Int>(days))
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                        textView.text = getString(R.string.alarm_set_success, "$time $date")
                        TtsManager.speak(getString(R.string.alarm_set_success, "$time $date"))
                    } else {
                        textView.text = getString(R.string.no_alarm_app)
                        TtsManager.speak(getString(R.string.no_alarm_app))
                    }
                } catch (e: Exception) {
                    textView.text = getString(R.string.alarm_set_error)
                    TtsManager.speak(getString(R.string.alarm_set_error))
                }
            }

            else -> TtsManager.speak(getString(R.string.command_not_recognized))
        }
    }

    override fun onDestroy() {
        TtsManager.shutdown()
        windowManager.removeView(overlayView)
        speechRecognizer.destroy()
        super.onDestroy()
    }
}
