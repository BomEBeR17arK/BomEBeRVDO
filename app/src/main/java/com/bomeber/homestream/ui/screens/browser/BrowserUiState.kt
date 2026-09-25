package com.bomeber.homestream.ui.screens.browser

/**
 * Immutable UI state for the Browser screen.
 * Produced by [BrowserViewModel], consumed by [BrowserScreen].
 */
data class BrowserUiState(
    val currentUrl: String = DEFAULT_URL,
    val urlBarText: String = DEFAULT_URL,
    val pageTitle: String = "",
    val isLoading: Boolean = false,
    val loadingProgress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        const val DEFAULT_URL = "https://www.google.com"
    }
}
