package com.sangwoo.push

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.sangwoo.push.enrollment.EnrollmentException
import com.sangwoo.push.enrollment.EnrollmentQrValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DeplApplication
    val history = app.notificationRepository.history.cachedIn(viewModelScope)

    private val _registered = MutableStateFlow(false)
    val registered: StateFlow<Boolean> = _registered.asStateFlow()
    private val _enrollmentState = MutableStateFlow<EnrollmentUiState>(EnrollmentUiState.Idle)
    val enrollmentState: StateFlow<EnrollmentUiState> = _enrollmentState.asStateFlow()

    private val validator = EnrollmentQrValidator(
        expectedProjectId = "depl-162ae",
        publicKeyX963Base64 = application.getString(R.string.enrollment_public_key_x963)
    )

    init {
        viewModelScope.launch { _registered.value = app.topicCoordinator.isRegistered() }
    }

    fun register(rawQr: String) {
        if (_enrollmentState.value is EnrollmentUiState.Working) return
        _enrollmentState.value = EnrollmentUiState.Working
        viewModelScope.launch {
            _enrollmentState.value = try {
                val payload = validator.validate(rawQr)
                app.topicCoordinator.register(payload)
                _registered.value = true
                EnrollmentUiState.Success
            } catch (error: EnrollmentException) {
                EnrollmentUiState.Error(error.message ?: "기기 등록에 실패했습니다.")
            } catch (_: Exception) {
                EnrollmentUiState.Error("네트워크 연결을 확인한 뒤 다시 시도해 주세요.")
            }
        }
    }

    fun resetEnrollment() {
        _enrollmentState.value = EnrollmentUiState.Working
        viewModelScope.launch {
            _enrollmentState.value = try {
                app.topicCoordinator.reset()
                _registered.value = false
                EnrollmentUiState.Idle
            } catch (error: EnrollmentException) {
                EnrollmentUiState.Error(error.message ?: "기기 등록 초기화에 실패했습니다.")
            }
        }
    }

    fun clearHistory() = viewModelScope.launch { app.notificationRepository.clear() }
    fun dismissStatus() { _enrollmentState.value = EnrollmentUiState.Idle }
}

sealed interface EnrollmentUiState {
    data object Idle : EnrollmentUiState
    data object Working : EnrollmentUiState
    data object Success : EnrollmentUiState
    data class Error(val message: String) : EnrollmentUiState
}
