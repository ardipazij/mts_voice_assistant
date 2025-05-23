package com.mtc.mtcai.core.device

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

class FlashlightController(private val context: Context) {

    private val cameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private var cameraId: String? = null

    init {
        try {
            cameraId = getCameraId()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleFlashlight(enable: Boolean) {
        try {
            cameraId?.let { id ->
                cameraManager.setTorchMode(id, enable)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getCameraId(): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                lensFacing == CameraCharacteristics.LENS_FACING_BACK && hasFlash
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun isFlashAvailable(): Boolean {
        return cameraId != null
    }
}