package com.mtc.mtcai

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.mtc.mtcai.modules.accessibility.MyAccessibilityService
import com.mtc.mtcai.modules.overlay.OverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private val Context.dataStore by preferencesDataStore(name = "settings")
    private val ASKED_ACCESSIBILITY_KEY = booleanPreferencesKey("asked_accessibility")

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { checkAndStartService() }

    private val accessibilityServiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { checkAndStartService() }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true &&
            permissions[Manifest.permission.CAMERA] == true &&
            permissions[Manifest.permission.CALL_PHONE] == true
            ) {
            checkAndStartService()
        } else {
            Toast.makeText(this, getString(R.string.microphone_and_camera_permissions_required), Toast.LENGTH_SHORT).show()
        }
    }

    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val currentLocale = resources.configuration.locales[0]
                tts.language = currentLocale
                speakInstructions()
            } else {
                Toast.makeText(this, getString(R.string.error_tts_initialization), Toast.LENGTH_SHORT)
                    .show()
            }
        }

        setContent {
            MainScreen()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val currentLocale = resources.configuration.locales[0]
            tts.language = currentLocale
            speakInstructions()
        } else {
            Toast.makeText(this, getString(R.string.error_tts_initialization), Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun speakInstructions() {
        tts.speak(
            getString(R.string.app_requirements) + " " +
            getString(R.string.overlay_permission) + " " +
            getString(R.string.accessibility_permission) + " " +
            getString(R.string.permission_note),
            TextToSpeech.QUEUE_FLUSH,
            null,
            null
        )
    }

    @Composable
    fun MainScreen() {
        val context = this@MainActivity
        val showAccessibilityWarning = remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val askedBefore = withContext(Dispatchers.IO) {
                context.getAskedAccessibility()
            }
            if (!askedBefore || !context.isAccessibilityServiceEnabled()) {
                showAccessibilityWarning.value = true
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = getString(R.string.app_requirements),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = getString(R.string.overlay_permission) + "\n" +
                        getString(R.string.accessibility_permission) + "\n" +
                        getString(R.string.permission_note),
                fontSize = 16.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(bottom = 24.dp)
            )

//            if (showAccessibilityWarning.value) {
//                Text(
//                    text = getString(R.string.accessibility_service_not_enabled),
//                    fontSize = 16.sp,
//                    textAlign = TextAlign.Start,
//                    modifier = Modifier.padding(bottom = 24.dp)
//                )
//            }

            Button(
                onClick = {
                    tts.stop()
                    checkAndStartService()
                }
            ) {
                Text(getString(R.string.start_assistant), fontSize = 18.sp)
            }
        }
    }

    private fun checkAndStartService() {
        when {
            !Settings.canDrawOverlays(this) -> requestOverlayPermission()

            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED-> {
                permissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.CALL_PHONE,  Manifest.permission.READ_CONTACTS))
            }

            else -> {
                lifecycleScope.launch {
                    val askedBefore = getAskedAccessibility()
                    if (!askedBefore) {
                        setAskedAccessibility(true)
                        requestAccessibilityService()
                    } else {
                        startService()
                    }
                }
            }
        }
    }

    @SuppressLint("UseKtx")
    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestAccessibilityService() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        accessibilityServiceLauncher.launch(intent)
    }

    private fun startService() {
        startService(Intent(this, OverlayService::class.java))
        moveTaskToBack(true)
        Toast.makeText(this, getString(R.string.service_started), Toast.LENGTH_SHORT).show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = ComponentName(this, MyAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(serviceName.flattenToString()) == true
    }

    private suspend fun setAskedAccessibility(value: Boolean) {
        applicationContext.dataStore.edit { preferences ->
            preferences[ASKED_ACCESSIBILITY_KEY] = value
        }
    }

    private suspend fun getAskedAccessibility(): Boolean {
        val preferences = applicationContext.dataStore.data.first()
        return preferences[ASKED_ACCESSIBILITY_KEY] ?: false
    }


    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        stopService(Intent(this, OverlayService::class.java))
        stopService(Intent(this, MyAccessibilityService::class.java))
        super.onDestroy()
    }
}