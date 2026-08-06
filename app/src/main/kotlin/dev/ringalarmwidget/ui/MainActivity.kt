package dev.ringalarmwidget.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import dev.ringalarmwidget.R
import dev.ringalarmwidget.core.panel.AlarmMode
import dev.ringalarmwidget.data.ThemeChoice
import dev.ringalarmwidget.ui.theme.LocalModePalette
import dev.ringalarmwidget.ui.theme.RingAlarmTheme
import dev.ringalarmwidget.widget.WidgetWork
import dev.ringalarmwidget.widget.anyWidgetPlaced
import kotlinx.coroutines.delay
import kotlin.math.ceil

class MainActivity : AppCompatActivity() {

    private val viewModel: PanelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (anyWidgetPlaced(this)) WidgetWork.schedule(this)
        setContent { AppRoot(this, viewModel) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumed()
    }

    override fun onStart() {
        super.onStart()
        viewModel.onVisible()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onHidden()
    }
}

@Composable
private fun AppRoot(activity: FragmentActivity, viewModel: PanelViewModel) {
    val theme by viewModel.theme.collectAsState()
    val widgetTheme by viewModel.widgetTheme.collectAsState()

    val dark = when (theme) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }

    RingAlarmTheme(dark = dark) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AppContent(activity, viewModel, theme, widgetTheme)
        }
    }
}

@Composable
private fun AppContent(
    activity: FragmentActivity,
    viewModel: PanelViewModel,
    theme: ThemeChoice,
    widgetTheme: ThemeChoice,
) {
    run {
        val state by viewModel.state.collectAsState()
        val confirmDisarm by viewModel.confirmDisarm.collectAsState()
        var showSettings by rememberSaveable { mutableStateOf(false) }

        val disarmTitle = stringResource(R.string.disarm_confirm_title)
        val disarmSubtitle = stringResource(R.string.disarm_confirm_subtitle)

        when (val current = state) {
            UiState.Loading -> Centered { CircularProgressIndicator() }

            is UiState.SignIn -> SignInScreen(current, viewModel::signIn)

            is UiState.TwoFactor -> TwoFactorScreen(current, viewModel::submitCode)

            is UiState.Panel -> if (showSettings) {
                SettingsScreen(
                    confirmDisarm = confirmDisarm,
                    canConfirm = activity.canConfirmIdentity(),
                    theme = theme,
                    widgetTheme = widgetTheme,
                    onConfirmDisarmChange = viewModel::setConfirmDisarm,
                    onThemeChange = viewModel::setTheme,
                    onWidgetThemeChange = viewModel::setWidgetTheme,
                    onSignOut = viewModel::signOut,
                    onClose = { showSettings = false },
                )
            } else {
                PanelScreen(
                    state = current,
                    onMode = { mode ->
                        if (mode == AlarmMode.DISARMED && confirmDisarm) {
                            activity.confirmIdentity(disarmTitle, disarmSubtitle) {
                                viewModel.setMode(mode)
                            }
                        } else {
                            viewModel.setMode(mode)
                        }
                    },
                    onRefresh = viewModel::refresh,
                    onSettings = { showSettings = true },
                    onConfirmBypass = viewModel::confirmBypass,
                    onDismissBypass = viewModel::dismissBypass,
                )
            }
        }
    }
}

@Composable
private fun ThemeChoiceGroup(label: String, theme: ThemeChoice, onChange: (ThemeChoice) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(
                label = stringResource(R.string.theme_system),
                selected = theme == ThemeChoice.SYSTEM,
                onClick = { onChange(ThemeChoice.SYSTEM) },
            )
            ChoiceChip(
                label = stringResource(R.string.theme_light),
                selected = theme == ThemeChoice.LIGHT,
                onClick = { onChange(ThemeChoice.LIGHT) },
            )
            ChoiceChip(
                label = stringResource(R.string.theme_dark),
                selected = theme == ThemeChoice.DARK,
                onClick = { onChange(ThemeChoice.DARK) },
            )
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        shape = MaterialTheme.shapes.extraSmall,
    )
}

@Composable
private fun SelectableRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = { content() },
    )
}

@Composable
private fun Screen(
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = horizontalAlignment,
        content = { content() },
    )
}

@Composable
private fun Logo(size: Int = 56, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3.4f).dp))
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size((size * 0.56f).dp),
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = { content() },
        )
    }
}

@Composable
private fun SignInScreen(state: UiState.SignIn, onSubmit: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Screen {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo()
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.onboarding_email_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.onboarding_password_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                stringResource(R.string.onboarding_password_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.error?.let { ErrorText(it) }

        PrimaryButton(
            label = stringResource(R.string.onboarding_submit),
            enabled = !state.busy && email.isNotBlank() && password.isNotBlank(),
            busy = state.busy,
            onClick = { onSubmit(email.trim(), password) },
        )

        Spacer(Modifier.height(4.dp))
        Disclaimer()
    }
}

@Composable
private fun TwoFactorScreen(state: UiState.TwoFactor, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }

    Screen {
        Logo()
        Text(stringResource(R.string.twofactor_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            state.prompt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(stringResource(R.string.twofactor_code_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        state.error?.let { ErrorText(it) }

        PrimaryButton(
            label = stringResource(R.string.twofactor_submit),
            enabled = !state.busy && code.isNotBlank(),
            busy = state.busy,
            onClick = { onSubmit(code.trim()) },
        )
    }
}

@Composable
private fun PanelScreen(
    state: UiState.Panel,
    onMode: (AlarmMode) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onConfirmBypass: () -> Unit,
    onDismissBypass: () -> Unit,
) {
    Screen {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Logo(size = 40)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                state.locationName?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.settings_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        StatusCard(state)

        state.bypass?.let {
            BypassCard(
                offer = it,
                onConfirm = onConfirmBypass,
                onDismiss = onDismissBypass,
            )
        }

        AlarmMode.entries.forEach { mode ->
            ModeButton(
                mode = mode,
                active = state.displayedMode == mode,
                enabled = !state.busy && state.bypass == null && state.displayedMode != mode,
                onClick = { onMode(mode) },
            )
        }

        state.message?.let { ErrorText(it) }

        TextButton(
            onClick = onRefresh,
            enabled = !state.busy && !state.refreshing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.widget_refresh))
        }

        Disclaimer()
    }
}

@Composable
private fun BypassCard(offer: BypassOffer, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val palette = LocalModePalette.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.panel_bypass_title),
                style = MaterialTheme.typography.titleSmall,
            )

            if (offer.sensorNames.isNotEmpty()) {
                Text(
                    offer.sensorNames.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.of(offer.mode),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                stringResource(R.string.panel_bypass_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onConfirm,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.panel_bypass_accept))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.panel_bypass_refuse))
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: UiState.Panel) {
    val palette = LocalModePalette.current
    val target = state.displayedMode?.let { palette.of(it) }
        ?: MaterialTheme.colorScheme.onSurfaceVariant
    val accent by animateColorAsState(targetValue = target, label = "status")
    val remaining = remainingSeconds(state.transitionEndsAt)

    Surface(
        color = accent,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.panel_current_state),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f),
            )
            Text(
                text = statusLabel(state),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            if (remaining > 0) {
                Text(
                    text = pluralStringResource(R.plurals.panel_transition_remaining, remaining, remaining),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            if (state.busy || remaining > 0 || state.refreshing) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun statusLabel(state: UiState.Panel): String {
    val mode = state.displayedMode ?: return stringResource(R.string.widget_state_unknown)
    return stringResource(modeLabel(mode))
}

@Composable
private fun remainingSeconds(endsAt: Long?): Int {
    var remaining by remember(endsAt) { mutableIntStateOf(secondsUntil(endsAt)) }

    LaunchedEffect(endsAt) {
        while (remaining > 0) {
            delay(TICK_MILLIS)
            remaining = secondsUntil(endsAt)
        }
    }

    return remaining
}

private fun secondsUntil(endsAt: Long?): Int {
    val left = (endsAt ?: return 0) - System.currentTimeMillis()
    return if (left <= 0) 0 else ceil(left / 1000.0).toInt()
}

private const val TICK_MILLIS = 250L

@Composable
private fun ModeButton(
    mode: AlarmMode,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalModePalette.current
    val accent = palette.of(mode)

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = if (active) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        contentColor = if (active) accent else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) accent else MaterialTheme.colorScheme.outline)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(modeLabel(mode)),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    confirmDisarm: Boolean,
    canConfirm: Boolean,
    theme: ThemeChoice,
    widgetTheme: ThemeChoice,
    onConfirmDisarmChange: (Boolean) -> Unit,
    onThemeChange: (ThemeChoice) -> Unit,
    onWidgetThemeChange: (ThemeChoice) -> Unit,
    onSignOut: () -> Unit,
    onClose: () -> Unit,
) {
    Screen {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
        }

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_confirm_disarm_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.settings_confirm_disarm_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = confirmDisarm, onCheckedChange = onConfirmDisarmChange)
            }

            if (confirmDisarm && !canConfirm) {
                Text(
                    stringResource(R.string.settings_confirm_disarm_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Card {
            Text(
                stringResource(R.string.settings_appearance_title),
                style = MaterialTheme.typography.titleSmall,
            )
            ThemeChoiceGroup(
                label = stringResource(R.string.settings_appearance_app),
                theme = theme,
                onChange = onThemeChange,
            )
            ThemeChoiceGroup(
                label = stringResource(R.string.settings_appearance_widget),
                theme = widgetTheme,
                onChange = onWidgetThemeChange,
            )
        }

        Card {
            Text(
                stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.titleSmall,
            )

            val systemLabel = stringResource(R.string.language_system)
            val current = currentLanguageTag()

            LanguageOptions.forEach { option ->
                SelectableRow(
                    label = option.label ?: systemLabel,
                    selected = option.tag == current,
                    onClick = { applyLanguage(option.tag) },
                )
            }
        }

        TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_sign_out), color = MaterialTheme.colorScheme.error)
        }

        Disclaimer()
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun modeLabel(mode: AlarmMode): Int = when (mode) {
    AlarmMode.DISARMED -> R.string.mode_disarmed
    AlarmMode.HOME -> R.string.mode_home
    AlarmMode.AWAY -> R.string.mode_away
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun Disclaimer() {
    Text(
        text = stringResource(R.string.app_disclaimer),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}
