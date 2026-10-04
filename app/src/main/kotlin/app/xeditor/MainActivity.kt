package app.xeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import app.xeditor.ui.BootAnimScreen
import app.xeditor.ui.EditorScreen
import app.xeditor.ui.EditorViewModel
import app.xeditor.ui.IconsScreen
import app.xeditor.ui.KeeperScreen
import app.xeditor.ui.theme.XEditorTheme

private enum class Tab(val label: String, val icon: ImageVector) {
    EDITOR("Builder", Icons.Outlined.Palette),
    BOOT("Boot animation", Icons.Outlined.PlayCircle),
    KEEPER("Keeper", Icons.Outlined.Shield),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            XEditorTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun App() {
    var tab by rememberSaveable { mutableStateOf(Tab.EDITOR) }
    var iconsOpen by rememberSaveable { mutableStateOf(false) }
    val editor: EditorViewModel = viewModel()

    Box(Modifier.fillMaxSize()) {
        Tabs(tab, { tab = it }, editor, onOpenIcons = { iconsOpen = true })
        // Drawn over the tabs (not instead of them) so the editor keeps its place.
        if (iconsOpen) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                IconsScreen(
                    onBack = { iconsOpen = false },
                    onDone = { iconsOpen = false; editor.refresh() },
                )
            }
        }
    }
}

@Composable
private fun Tabs(tab: Tab, setTab: (Tab) -> Unit, editor: EditorViewModel, onOpenIcons: () -> Unit) {
    Scaffold(
        // Each tab draws its own top app bar, which already handles the status bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            setTab(t)
                            if (t == Tab.EDITOR) editor.refresh()
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.EDITOR -> EditorScreen(
                    onOpenIcons = onOpenIcons,
                    onOpenBootAnimation = { setTab(Tab.BOOT) },
                    vm = editor,
                )
                Tab.BOOT -> BootAnimScreen(onAddedToTheme = { editor.refresh() })
                Tab.KEEPER -> KeeperScreen()
            }
        }
    }
}
