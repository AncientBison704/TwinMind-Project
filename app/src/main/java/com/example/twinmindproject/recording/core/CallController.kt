package com.example.twinmindproject.recording.core

import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import java.util.concurrent.Executor
import android.os.Handler
import android.os.Looper

class CallController(
    private val tm: TelephonyManager,
    private val hasPhoneStatePermission: () -> Boolean,
    private val onCallActive: () -> Unit,
    private val onCallIdle: () -> Unit
) {
    private var legacyListener: PhoneStateListener? = null
    private var modernCallback: TelephonyCallback? = null

    fun register() {
        if (!hasPhoneStatePermission()) return

        if (Build.VERSION.SDK_INT >= 31) {
            val exec: Executor = Executor { r -> Handler(Looper.getMainLooper()).post(r) }
            modernCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    when (state) {
                        TelephonyManager.CALL_STATE_RINGING,
                        TelephonyManager.CALL_STATE_OFFHOOK -> onCallActive()
                        TelephonyManager.CALL_STATE_IDLE -> onCallIdle()
                    }
                }
            }
            try {
                tm.registerTelephonyCallback(exec, modernCallback as TelephonyCallback)
            } catch (_: SecurityException) {  }

        } else {
            // Pre-Android 12: use PhoneStateListener
            legacyListener = object : PhoneStateListener() {
                @Deprecated("Deprecated in API 31", ReplaceWith("TelephonyCallback"))
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    when (state) {
                        TelephonyManager.CALL_STATE_RINGING,
                        TelephonyManager.CALL_STATE_OFFHOOK -> onCallActive()
                        TelephonyManager.CALL_STATE_IDLE -> onCallIdle()
                    }
                }
            }
            try {
                @Suppress("DEPRECATION")
                tm.listen(legacyListener, PhoneStateListener.LISTEN_CALL_STATE)
            } catch (_: SecurityException) {  }
        }
    }

    fun unregister() {
        if (Build.VERSION.SDK_INT >= 31) {
            modernCallback?.let {
                runCatching { tm.unregisterTelephonyCallback(it) }
            }
            modernCallback = null
        } else {
            @Suppress("DEPRECATION")
            legacyListener?.let { runCatching { tm.listen(it, PhoneStateListener.LISTEN_NONE) } }
            legacyListener = null
        }
    }
}

