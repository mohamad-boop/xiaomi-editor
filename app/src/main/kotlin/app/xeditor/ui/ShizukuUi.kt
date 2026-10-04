package app.xeditor.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.xeditor.shizuku.ShizukuShell

/** Shows what Shizuku unlocks and the one step needed to get there. */
@Composable
fun ShizukuCard(status: ShizukuShell.Status, why: String, onRequest: () -> Unit) {
    val ctx = LocalContext.current
    SectionCard("Shizuku", Icons.Outlined.AdminPanelSettings, subtitle = status.label) {
        Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (status) {
                ShizukuShell.Status.NOT_INSTALLED -> FilledTonalButton(onClick = {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${ShizukuShell.PACKAGE}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("Get Shizuku") }
                ShizukuShell.Status.NOT_RUNNING -> FilledTonalButton(onClick = { ShizukuShell.openShizukuApp(ctx) }) { Text("Open Shizuku") }
                ShizukuShell.Status.NO_PERMISSION -> FilledTonalButton(onClick = onRequest) { Text("Allow") }
                ShizukuShell.Status.READY -> Unit
            }
        }
    }
}
