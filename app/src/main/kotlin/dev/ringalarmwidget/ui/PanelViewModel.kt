package dev.ringalarmwidget.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ringalarmwidget.R
import dev.ringalarmwidget.core.auth.AuthResult
import dev.ringalarmwidget.core.auth.TokenLease
import dev.ringalarmwidget.core.panel.AlarmMode
import dev.ringalarmwidget.core.panel.PanelResult
import dev.ringalarmwidget.core.panel.PanelSnapshot
import dev.ringalarmwidget.data.AppContainer
import dev.ringalarmwidget.data.PanelOutcome
import dev.ringalarmwidget.data.PanelRepository
import dev.ringalarmwidget.data.ThemeChoice
import dev.ringalarmwidget.widget.updateWidgets
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Loading : UiState

    data class SignIn(
        val busy: Boolean = false,
        val error: String? = null,
    ) : UiState

    data class TwoFactor(
        val prompt: String,
        val busy: Boolean = false,
        val error: String? = null,
    ) : UiState

    data class Panel(
        val locationName: String?,
        val mode: AlarmMode?,
        val transitioning: Boolean,
        val transitionEndsAt: Long? = null,
        val pending: AlarmMode? = null,
        val refreshing: Boolean = false,
        val message: String? = null,
    ) : UiState {
        val busy: Boolean get() = pending != null

        val displayedMode: AlarmMode? get() = pending ?: mode
    }
}

class PanelViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PanelRepository(application)
    private val container = AppContainer.get(application)

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _confirmDisarm = MutableStateFlow(false)
    val confirmDisarm: StateFlow<Boolean> = _confirmDisarm.asStateFlow()

    private val _theme = MutableStateFlow(ThemeChoice.SYSTEM)
    val theme: StateFlow<ThemeChoice> = _theme.asStateFlow()

    private val _widgetTheme = MutableStateFlow(ThemeChoice.SYSTEM)
    val widgetTheme: StateFlow<ThemeChoice> = _widgetTheme.asStateFlow()

    private var pendingEmail: String? = null
    private var pendingPassword: String? = null
    private var loadJob: Job? = null
    private var transitionJob: Job? = null
    private var watchJob: Job? = null

    init {
        viewModelScope.launch {
            _confirmDisarm.value = repository.store.confirmDisarm()
            _theme.value = repository.store.themeChoice()
            _widgetTheme.value = repository.store.widgetThemeChoice()
        }
        viewModelScope.launch {
            restoreCache()
            refresh()
        }
    }

    fun setConfirmDisarm(enabled: Boolean) {
        _confirmDisarm.value = enabled
        viewModelScope.launch { repository.store.setConfirmDisarm(enabled) }
    }

    fun setTheme(choice: ThemeChoice) {
        _theme.value = choice
        viewModelScope.launch { repository.store.setThemeChoice(choice) }
    }

    fun setWidgetTheme(choice: ThemeChoice) {
        _widgetTheme.value = choice
        viewModelScope.launch {
            repository.store.setWidgetThemeChoice(choice)
            updateWidgets(context())
        }
    }

    fun signIn(email: String, password: String) {
        pendingEmail = email
        pendingPassword = password
        _state.value = UiState.SignIn(busy = true)

        viewModelScope.launch {
            when (val result = container.auth().signIn(email, password)) {
                is AuthResult.Success -> {
                    container.sessionManager().adopt(result.session)
                    forgetCredentials()
                    refresh()
                }

                is AuthResult.TwoFactorRequired ->
                    _state.value = UiState.TwoFactor(prompt = context().messageFor(result.prompt))

                else -> _state.value = UiState.SignIn(error = context().messageFor(result))
            }
        }
    }

    fun submitCode(code: String) {
        val email = pendingEmail
        val password = pendingPassword
        if (email == null || password == null) {
            _state.value = UiState.SignIn()
            return
        }

        val current = _state.value as? UiState.TwoFactor ?: return
        _state.value = current.copy(busy = true, error = null)

        viewModelScope.launch {
            when (val result = container.auth().signIn(email, password, code)) {
                is AuthResult.Success -> {
                    container.sessionManager().adopt(result.session)
                    forgetCredentials()
                    refresh()
                }

                else -> _state.value = current.copy(error = context().messageFor(result))
            }
        }
    }

    fun refresh() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch { loadPanel() }
    }

    fun onResumed() {
        val current = _state.value
        if (current is UiState.Panel && !current.busy) refresh()
    }

    fun onVisible() {
        if (watchJob?.isActive == true) return
        watchJob = viewModelScope.launch {
            while (isActive) {
                delay(VISIBLE_POLL_MILLIS)
                val current = _state.value
                if (current is UiState.Panel && !current.busy) refresh()
            }
        }
    }

    fun onHidden() {
        watchJob?.cancel()
        watchJob = null
    }

    fun setMode(mode: AlarmMode) {
        val current = _state.value as? UiState.Panel ?: return
        loadJob?.cancel()
        transitionJob?.cancel()
        _state.value = current.copy(pending = mode, refreshing = false, message = null)

        viewModelScope.launch { apply(repository.set(mode), current.locationName) }
    }

    fun signOut() {
        viewModelScope.launch {
            container.sessionManager().signOut()
            _state.value = UiState.SignIn()
        }
    }

    private suspend fun restoreCache() {
        val cache = repository.store.panelCache() ?: return
        if (cache.mode == null) return
        if (_state.value !is UiState.Loading) return

        _state.value = UiState.Panel(
            locationName = cache.locationName,
            mode = cache.mode,
            transitioning = false,
            refreshing = true,
        )
    }

    private suspend fun loadPanel() {
        val known = _state.value as? UiState.Panel
        _state.value = if (known?.mode != null) {
            known.copy(refreshing = true, message = null)
        } else {
            UiState.Loading
        }

        apply(repository.read(), known?.locationName)
    }

    private suspend fun apply(outcome: PanelOutcome, locationName: String?) {
        when (outcome) {
            is PanelOutcome.Ready -> publish(outcome.locationName ?: locationName, outcome.snapshot)

            PanelOutcome.SignedOut -> _state.value = UiState.SignIn()

            PanelOutcome.NoLocation -> _state.value = UiState.Panel(
                locationName = null,
                mode = null,
                transitioning = false,
                message = context().getString(R.string.error_no_location),
            )

            is PanelOutcome.Unavailable -> {
                val current = _state.value as? UiState.Panel
                _state.value = if (current?.mode != null) {
                    current.copy(
                        pending = null,
                        refreshing = false,
                        message = context().messageFor(TokenLease.Unavailable(outcome.cause)),
                    )
                } else {
                    UiState.SignIn(error = context().messageFor(TokenLease.Unavailable(outcome.cause)))
                }
            }

            is PanelOutcome.Failed -> fail(locationName, outcome.result)
        }
    }

    private suspend fun publish(locationName: String?, snapshot: PanelSnapshot) {
        _state.value = UiState.Panel(
            locationName = locationName,
            mode = snapshot.mode,
            transitioning = snapshot.transitioning,
            transitionEndsAt = snapshot.transitionEndsAtEpochMillis,
        )
        scheduleTransitionRefresh(snapshot.transitionEndsAtEpochMillis)
        updateWidgets(context())
    }

    private fun scheduleTransitionRefresh(endsAt: Long?) {
        transitionJob?.cancel()
        if (endsAt == null) return

        val wait = endsAt - System.currentTimeMillis()
        if (wait <= 0) return

        transitionJob = viewModelScope.launch {
            delay(wait + TRANSITION_SETTLE_MILLIS)
            refresh()
        }
    }

    private fun fail(locationName: String?, result: PanelResult) {
        val current = _state.value as? UiState.Panel
        _state.value = UiState.Panel(
            locationName = locationName ?: current?.locationName,
            mode = current?.mode,
            transitioning = false,
            message = describe(result),
        )
    }

    private fun describe(result: PanelResult): String = when (result) {
        is PanelResult.Ready -> ""
        PanelResult.NoSecurityPanel, PanelResult.NoUsableAsset ->
            context().getString(R.string.error_no_panel)

        is PanelResult.Rejected -> context().getString(R.string.error_mode_rejected)
        is PanelResult.TimedOut -> context().getString(R.string.error_timeout)
        is PanelResult.Unreachable -> context().getString(R.string.error_unreachable)
    }

    private fun forgetCredentials() {
        pendingEmail = null
        pendingPassword = null
    }

    private fun context() = getApplication<Application>()

    private companion object {
        const val TRANSITION_SETTLE_MILLIS = 1_500L
        const val VISIBLE_POLL_MILLIS = 60_000L
    }
}
