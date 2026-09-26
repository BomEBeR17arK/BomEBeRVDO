package com.bomeber.homestream.ui.screens.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bomeber.homestream.HomeStreamApplication
import com.bomeber.homestream.download.DownloadEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as HomeStreamApplication).downloadRepository

    val downloads: StateFlow<List<DownloadEntity>> = repository.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun pause(id: String) = repository.pause(id)
    fun resume(id: String) = repository.resume(id)
    fun cancel(id: String) = repository.cancel(id)
    fun retry(id: String) = repository.retry(id)
    /** Step 5.1 fix — was missing; DAO already had deleteDownload() unused. */
    fun remove(id: String) = repository.remove(id)
}