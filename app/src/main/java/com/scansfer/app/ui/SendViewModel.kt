package com.scansfer.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scansfer.app.core.TransferProfile
import com.scansfer.app.send.SenderEngine
import com.scansfer.app.util.VideoInfo
import com.scansfer.app.util.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SendViewModel(application: Application) : AndroidViewModel(application) {

    var video by mutableStateOf<VideoInfo?>(null)
        private set

    var profile by mutableStateOf(TransferProfile.DEFAULT)
        private set

    var inspecting by mutableStateOf(false)
        private set

    var pickError by mutableStateOf<String?>(null)
        private set

    var engine by mutableStateOf<SenderEngine?>(null)
        private set

    private var beamJob: Job? = null

    fun choose(uri: Uri?) {
        if (uri == null) return
        pickError = null
        inspecting = true
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                runCatching { VideoSource.inspect(getApplication(), uri) }.getOrNull()
            }
            inspecting = false
            if (info == null) {
                pickError = "That file could not be read. Try another video."
            } else if (info.sizeBytes > VideoSource.MAX_BYTES) {
                pickError = "That video is too large to send over QR."
            } else {
                video = info
            }
        }
    }

    fun selectProfile(next: TransferProfile) {
        if (engine == null) profile = next
    }

    fun clear() {
        stop()
        video = null
        pickError = null
    }

    fun start() {
        val source = video ?: return
        if (engine != null) return
        val created = SenderEngine(getApplication(), source, profile)
        engine = created
        beamJob = viewModelScope.launch { created.run() }
    }

    fun stop() {
        beamJob?.cancel()
        beamJob = null
        engine = null
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
