//package com.example.twinmindproject.recording.core
//
//import android.media.AudioDeviceInfo
//import android.media.AudioManager
//import android.os.Build
//
///**
// * Listens for audio I/O device add/remove and reports human-readable messages
// * such as "Wired headset connected" / "Wired headset disconnected".
// */
//class AudioRouteController(
//    private val audioManager: AudioManager,
//    private val onInputChanged: (String) -> Unit
//) {
//    private val cb = object : AudioManager.AudioDeviceCallback() {
//        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
//            val msg = addedDevices.firstNotNullOfOrNull { toMessage(it, true) } ?: return
//            onInputChanged(msg)
//        }
//        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
//            val msg = removedDevices.firstNotNullOfOrNull { toMessage(it, false) } ?: return
//            onInputChanged(msg)
//        }
//    }
//
//    fun register() {
//        // null handler => main thread
//        audioManager.registerAudioDeviceCallback(cb, null)
//    }
//
//    fun unregister() {
//        try { audioManager.unregisterAudioDeviceCallback(cb) } catch (_: Exception) { }
//    }
//
//    private fun toMessage(info: AudioDeviceInfo, added: Boolean): String? {
//        // We only care about INPUT devices (microphones)
//        if (info.type !in INPUT_TYPES) return null
//        val name = when (info.type) {
//            AudioDeviceInfo.TYPE_WIRED_HEADSET   -> "Wired headset mic"
//            AudioDeviceInfo.TYPE_USB_HEADSET     -> "USB headset mic"
//            AudioDeviceInfo.TYPE_BUILTIN_MIC     -> "Built-in mic"
//            AudioDeviceInfo.TYPE_WIRED_HEADPHONES-> "Wired headphones" // usually no mic
//            else -> "Input"
//        }
//        return if (added) "$name connected" else "$name disconnected"
//    }
//
//    companion object {
//        // what we consider "input related" for user messaging
//        private val INPUT_TYPES = setOf(
//            AudioDeviceInfo.TYPE_WIRED_HEADSET,
//            AudioDeviceInfo.TYPE_USB_HEADSET,
//            AudioDeviceInfo.TYPE_BUILTIN_MIC,
//            AudioDeviceInfo.TYPE_WIRED_HEADPHONES
//        )
//    }
//}
