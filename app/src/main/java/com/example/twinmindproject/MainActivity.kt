package com.example.twinmindproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.twinmindproject.recording.RecordingViewModel
import com.example.twinmindproject.ui.RecordScreen

class MainActivity : ComponentActivity() {
    private val recordVm by viewModels<RecordingViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RecordScreen(recordVm)
        }
    }
}
