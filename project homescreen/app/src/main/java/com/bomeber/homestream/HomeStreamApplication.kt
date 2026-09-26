package com.bomeber.homestream

import android.app.Application
import com.bomeber.homestream.download.DownloadRepository

class HomeStreamApplication : Application() {
    /** App-lifetime singleton — see DownloadRepository doc for why no DI framework is used. */
    val downloadRepository: DownloadRepository by lazy { DownloadRepository(this) }
}