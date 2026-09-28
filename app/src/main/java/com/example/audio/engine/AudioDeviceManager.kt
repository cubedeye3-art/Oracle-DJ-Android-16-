package com.example.audio.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi

class AudioDeviceManager(
    private val context: Context,
    private val onHeadphonesDisconnected: () -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var noisyReceiver: BroadcastReceiver? = null

    var nativeSampleRate: Int = 48000
        private set
    var nativeFramesPerBuffer: Int = 192
        private set
    var currentDeviceName: String = "Built-in Stereo Speakers"
        private set
    var isUsbAudio: Boolean = false
        private set
    var isBluetoothAudio: Boolean = false
        private set
    var thermalStatusString: String = "Normal"
        private set

    init {
        detectHardwareCapabilities()
        registerNoisyReceiver()
        registerThermalListener()
    }

    private fun detectHardwareCapabilities() {
        val srProp = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        val framesProp = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)

        nativeSampleRate = srProp?.toIntOrNull() ?: 48000
        nativeFramesPerBuffer = framesProp?.toIntOrNull() ?: 256

        queryConnectedAudioDevices()
    }

    fun queryConnectedAudioDevices() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var name = "POCO M8 Audio Subsystem"
        isUsbAudio = false
        isBluetoothAudio = false

        for (dev in devices) {
            when (dev.type) {
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET -> {
                    name = "USB Hi-Res Audio Interface (${dev.productName})"
                    isUsbAudio = true
                    break
                }
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET -> {
                    name = "Bluetooth A2DP (${dev.productName})"
                    isBluetoothAudio = true
                }
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> {
                    name = "Wired 3.5mm Headphone Jack"
                }
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                    if (!isBluetoothAudio) {
                        name = "POCO M8 Dual Stereo Speakers"
                    }
                }
            }
        }
        currentDeviceName = name
    }

    private fun registerNoisyReceiver() {
        noisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    onHeadphonesDisconnected()
                }
            }
        }
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        context.registerReceiver(noisyReceiver, filter)
    }

    private fun registerThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.addThermalStatusListener { status ->
                    thermalStatusString = when (status) {
                        PowerManager.THERMAL_STATUS_NONE -> "Nominal (Cool)"
                        PowerManager.THERMAL_STATUS_LIGHT -> "Light Warmth"
                        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate (Throttling DSP)"
                        PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttling"
                        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical Heat"
                        else -> "Normal"
                    }
                }
            } catch (e: Exception) {
                thermalStatusString = "Normal"
            }
        }
    }

    fun release() {
        noisyReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
