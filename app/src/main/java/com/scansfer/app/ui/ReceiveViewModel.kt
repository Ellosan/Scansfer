package com.scansfer.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scansfer.app.receive.QrAnalyzer
import com.scansfer.app.receive.ReceiverEngine

class ReceiveViewModel(application: Application) : AndroidViewModel(application) {

    val engine = ReceiverEngine(application, viewModelScope)

    val analyzer = QrAnalyzer(
        onFrame = engine::onFrame,
        onDetection = engine::onDetection,
    )

    var torchEnabled by mutableStateOf(false)
        private set

    fun toggleTorch() {
        torchEnabled = !torchEnabled
    }

    fun reset() = engine.reset()
}
