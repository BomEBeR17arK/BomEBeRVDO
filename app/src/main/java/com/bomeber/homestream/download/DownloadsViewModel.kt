package com.bomeber.homestream.ui.screens.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bomeber.homestream.download.DownloadEntity
import com.bomeber.homestream.download.DownloadRepository
import com.bomeber.homestream.media.DetectedMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DownloadRepository.getInstance(application)

    val downloads: StateFlow<List<DownloadEntity>> = repository.downloads.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { repository.resumeInterruptedDownloads() }
    }

    /** Called from BrowserScreen's "Download" action (see HomeStreamNavHost). */
    fun onDownloadMedia(media: DetectedMedia) {
        viewModelScope.launch {
            try {
                repository.enqueueDownload(media)
            } catch (e: DownloadRepository.UnsupportedDownloadType) {
                _message.value = e.message
            }
        }
    }

    fun onPause(id: String) = viewModelScope.launch { repository.pause(id) }
    fun onResume(id: String) = viewModelScope.launch { repository.resume(id) }
    fun onCancel(id: String) = viewModelScope.launch { repository.cancel(id) }
    fun onRetry(id: String) = viewModelScope.launch { repository.retry(id) }
    fun onRemove(id: String) = viewModelScope.launch { repository.remove(id) }

    fun messageShown() { _message.value = null }
}