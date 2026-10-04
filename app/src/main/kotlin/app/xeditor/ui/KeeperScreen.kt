package app.xeditor.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.xeditor.keeper.Keeper
import app.xeditor.keeper.SetupChecks
import app.xeditor.keeper.SetupStep
import app.xeditor.shizuku.ShizukuShell
import app.xeditor.util.displayNameOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class KeeperState(
    val themeName: String? = null,
    val enabled: Boolean = false,
    val lastReapply: Long = 0L,
    val steps: List<Pair<SetupStep, Boolean?>> = emptyList(),
    val shizuku: ShizukuShell.Status = ShizukuShell.Status.NOT_INSTALLED,
    val canApplyDirectly: Boolean = true,
    val busy: String? = null,
    val message: String? = null,
)

class KeeperViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val _state = MutableStateFlow(KeeperState())
    val state: StateFlow<KeeperState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update {
            it.copy(
                themeName = Keeper.themeName(ctx),
                enabled = Keeper.isEnabled(ctx),
                lastReapply = Keeper.lastReapply(ctx),
                steps = SetupChecks.visibleSteps(ctx).map { s -> s to SetupChecks.status(ctx, s) },
                shizuku = ShizukuShell.status(ctx),
                canApplyDirectly = app.xeditor.theme.ThemeApplier.hasThemeManager(ctx),
            )
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun pickTheme(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = "Saving theme…", message = null) }
            val msg = runCatching {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use {
                        Keeper.setTheme(ctx, ctx.displayNameOf(uri), it)
                    } ?: error("Can't read that file")
                }
                "Theme saved. Tap Re-apply now to apply it."
            }.getOrElse { it.message ?: it.toString() }
            _state.update { it.copy(busy = null, message = msg) }
            refresh()
        }
    }

    fun requestShizuku() = ShizukuShell.requestPermission { refresh() }

    fun grantAllWithShizuku() {
        viewModelScope.launch {
            _state.update { it.copy(busy = "Granting…", message = null) }
            val failed = withContext(Dispatchers.IO) { SetupChecks.grantAllWithShizuku(ctx) }
            val msg = if (failed.isEmpty()) "All permissions granted"
            else "Granted what this phone allows. Set these by hand: " + failed.joinToString(", ") { it.title }
            _state.update { it.copy(busy = null, message = msg) }
            refresh()
        }
    }

    fun setEnabled(on: Boolean) {
        Keeper.setEnabled(ctx, on)
        if (!on) viewModelScope.launch(Dispatchers.IO) { runCatching { app.xeditor.util.Downloads.cleanTemp(ctx, 0) } }
        refresh()
    }

    fun applyNow(launchContext: Context) {
        viewModelScope.launch {
            _state.update { it.copy(busy = "Applying…", message = null) }
            // Publishing may copy into Downloads, so do it off the main thread first.
            withContext(Dispatchers.IO) { Keeper.ensurePublished(ctx) }
            val outcome = Keeper.reapply(launchContext, fromBackground = false)
            val msg = when (outcome) {
                Keeper.Outcome.LAUNCHED -> "Sent to the theme manager"
                Keeper.Outcome.OPENED_THEMES -> "In Themes, open My account → Themes, tap your theme and tap Apply"
                Keeper.Outcome.NOTIFIED -> "Couldn't open the theme manager — use the notification"
                Keeper.Outcome.NO_THEME -> "Pick a theme first"
                Keeper.Outcome.NO_THEME_MANAGER -> "This phone has no MIUI/HyperOS theme manager"
            }
            _state.update { it.copy(busy = null, message = msg) }
            refresh()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeeperScreen(vm: KeeperViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(Unit) { vm.refresh() }
    // Setup steps are finished in other apps' screens; re-check when we come back.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.refresh() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    val pickMtz = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::pickTheme)
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refresh()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Theme keeper") })
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusBanner(s.busy, s.message, vm::dismissMessage) }
            if (s.canApplyDirectly) item {
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                ) {
                    Text(
                        "This phone's theme manager can re-apply themes: the keeper does it after every reboot, and Re-apply now does it " +
                            "any time. If the Themes app asks you to pick a file, choose keeper.mtz in Download › XiaomiEditor › temp.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            if (!s.canApplyDirectly) item {
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                ) {
                    Text(
                        "This HyperOS version has no way for apps to re-apply a theme, so the keeper can only remind you: " +
                            "after a reset it opens the Themes app (or posts a notification) and you tap Apply yourself.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            item {
                SectionCard(
                    "Keep my theme applied", Icons.Outlined.Shield,
                    subtitle = "HyperOS drops imported themes and fonts, usually after a reboot. The keeper puts yours back each time the phone starts. No root needed.",
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.themeName ?: "No theme chosen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    s.themeName == null -> "Use Apply & keep in the editor, or pick an .mtz"
                                    s.enabled -> "Active: re-applies after every reboot"
                                    else -> "Off"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (s.lastReapply > 0) {
                                Text(
                                    "Last applied " + DateUtils.getRelativeTimeSpanString(s.lastReapply),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(checked = s.enabled, onCheckedChange = vm::setEnabled, enabled = s.themeName != null)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.applyNow(ctx) }, enabled = s.themeName != null && s.busy == null) { Text("Re-apply now") }
                        OutlinedButton(onClick = { pickMtz.launch(arrayOf("*/*")) }) { Text("Pick .mtz") }
                    }
                }
            }
            item {
                ShizukuCard(
                    s.shizuku,
                    if (s.shizuku == ShizukuShell.Status.READY)
                        "The keeper re-applies your theme through Shizuku after boot, so the pop-up permissions below are only a fallback."
                    else "With Shizuku the keeper can put your theme back after a reboot without any pop-up permissions, " +
                        "and grant the checklist below in one tap. Shizuku must be running after the reboot (root, or its start-on-boot option).",
                    vm::requestShizuku,
                )
            }
            item {
                val done = s.steps.count { it.second == true }
                SectionCard(
                    "Setup checklist", Icons.Outlined.Checklist,
                    subtitle = "$done of ${s.steps.size} done. HyperOS stops background apps hard, so the keeper needs all of these.",
                ) {
                    if (s.shizuku == ShizukuShell.Status.READY && s.steps.any { it.second != true }) {
                        Button(onClick = vm::grantAllWithShizuku, enabled = s.busy == null) { Text("Grant all with Shizuku") }
                    }
                    s.steps.forEach { (step, status) ->
                        StepRow(step, status) {
                            if (step == SetupStep.NOTIFICATIONS && Build.VERSION.SDK_INT >= 33 && status == false) {
                                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                SetupChecks.open(ctx, step)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: SetupStep, status: Boolean?, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when (status) {
                true -> Icons.Filled.CheckCircle
                false -> Icons.Outlined.RadioButtonUnchecked
                null -> Icons.Outlined.HelpOutline
            },
            contentDescription = when (status) { true -> "Done"; false -> "Not done"; null -> "Unknown" },
            tint = if (status == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(step.title, style = MaterialTheme.typography.bodyLarge)
            Text(step.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (status != true) {
            if (status == null) TextButton(onClick = onFix) { Text("Open") }
            else FilledTonalButton(onClick = onFix) { Text("Fix") }
        }
    }
}
