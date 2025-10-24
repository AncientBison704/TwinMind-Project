package com.example.twinmindproject.recording

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class RecordingUi(
    val status: RecordingStatus = RecordingStatus.Idle,
    val noAudio: Boolean = false,
    val pauseReason: String? = null
)

class RecordingViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RecordingRepository(app)

    private val _ui = MutableStateFlow(RecordingUi())
    val ui: StateFlow<RecordingUi> = _ui.asStateFlow()

    fun bind() {
        repo.startForegroundRecordingService()
        repo.bind {
            repo.status
                ?.onEach { s -> _ui.value = _ui.value.copy(status = s) }
                ?.launchIn(viewModelScope)

            repo.silence
                ?.onEach { flag -> _ui.value = _ui.value.copy(noAudio = flag) }
                ?.launchIn(viewModelScope)

            repo.pauseReason
                ?.onEach { reason -> _ui.value = _ui.value.copy(pauseReason = reason) }
                ?.launchIn(viewModelScope)
        }
    }

    fun unbind() = repo.unbind()

    fun pause()  = repo.pause()
    fun resume() = repo.resume()

    fun onRecordPressed(current: RecordingStatus) {
        when (current) {
            RecordingStatus.Idle,
            is RecordingStatus.Stopped -> repo.start()
            is RecordingStatus.Paused   -> repo.resume()
            is RecordingStatus.Recording-> repo.pause()
            is RecordingStatus.Error    -> Unit
        }
    }

    fun onStopPressed() = repo.stop()
}

