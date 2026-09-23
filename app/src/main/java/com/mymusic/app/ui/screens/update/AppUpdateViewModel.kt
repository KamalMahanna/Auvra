package com.mymusic.app.ui.screens.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusic.app.data.model.AppRelease
import com.mymusic.app.utils.AppUpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val appUpdateChecker: AppUpdateChecker
) : ViewModel() {

    private val _availableUpdate = MutableStateFlow<AppRelease?>(null)
    val availableUpdate: StateFlow<AppRelease?> = _availableUpdate.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    val installedVersion: String = appUpdateChecker.getInstalledVersionName()

    init {
        // Automatically check for update once when opening the app
        checkForUpdate(force = false)
    }

    fun checkForUpdate(force: Boolean = false) {
        viewModelScope.launch {
            _isChecking.value = true
            try {
                val update = appUpdateChecker.checkForUpdate(force)
                _availableUpdate.value = update
            } finally {
                _isChecking.value = false
            }
        }
    }

    fun dismissUpdate(release: AppRelease) {
        appUpdateChecker.dismissRelease(release.tagName)
        _availableUpdate.value = null
    }

    fun dismissForNow() {
        _availableUpdate.value = null
    }
}

