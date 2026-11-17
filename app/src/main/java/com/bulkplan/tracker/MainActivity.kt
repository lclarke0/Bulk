@file:OptIn(ExperimentalMaterial3Api::class)

package com.bulkplan.tracker

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.logic.PlanLogic
import com.bulkplan.tracker.ui.BulkTheme
import com.bulkplan.tracker.ui.CoachScreen
import com.bulkplan.tracker.ui.FoodScreen
import com.bulkplan.tracker.ui.ProgressScreen
import com.bulkplan.tracker.ui.SettingsScreen
import com.bulkplan.tracker.ui.TodayScreen
import com.bulkplan.tracker.ui.WorkoutScreen
import kotlinx.coroutines.launch

/** Lets any screen show a short message at the bottom. */
val LocalToast = staticCompositionLocalOf<(String) -> Unit> { {} }

class MainActivity : ComponentActivity() {
    private val tabFromIntent = mutableStateOf<String?>(null)

    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(this)
        tabFromIntent.value = intent?.getStringExtra("tab")
        if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { BulkTheme { App(tabFromIntent.value) { tabFromIntent.value = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabFromIntent.value = intent.getStringExtra("tab")
    }

    override fun onStop() {
        super.onStop()
        Repo.writeNow()
    }
}

private data class Tab(val key: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    Tab("today", "Today", Icons.Filled.Check),
    Tab("food", "Food", Icons.Filled.ShoppingCart),
    Tab("workout", "Workout", Icons.Filled.Star),
    Tab("progress", "Progress", Icons.Filled.DateRange),
    Tab("coach", "Coach", Icons.Filled.Favorite),
)

@Composable
fun App(requestedTab: String?, consumed: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf("today") }
    var settings by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(requestedTab) { if (requestedTab != null) { tab = requestedTab; settings = false; consumed() } }
    val state by Repo.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toast: (String) -> Unit = { m -> scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(m) } }

    CompositionLocalProvider(LocalToast provides toast) {
        if (settings) {
            SettingsScreen(onClose = { settings = false })
            return@CompositionLocalProvider
        }
        val t = PlanLogic.today(); val w = PlanLogic.weekOf(state.plan, t); val b = PlanLogic.blockFor(state.plan, w)
        val sub = when {
            w < 1 -> "Starts ${PlanLogic.fmtD(state.plan.startDate)} · ${PlanLogic.diffD(t, state.plan.startDate)} days"
            w > state.plan.weeks -> "Plan complete"
            else -> "Week $w of ${state.plan.weeks}" + (b?.let { " · ${it.name}" } ?: "")
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(TABS.first { it.key == tab }.label, fontSize = 26.sp)
                            Text("${PlanLogic.fmtD(t)} · $sub", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = { IconButton(onClick = { settings = true }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TABS.forEach { tb ->
                        NavigationBarItem(
                            selected = tab == tb.key, onClick = { tab = tb.key },
                            icon = { Icon(tb.icon, contentDescription = null) }, label = { Text(tb.label) },
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    "today" -> TodayScreen(onGo = { tab = it })
                    "food" -> FoodScreen()
                    "workout" -> WorkoutScreen()
                    "progress" -> ProgressScreen()
                    "coach" -> CoachScreen(onOpenSettings = { settings = true })
                }
            }
        }
    }
}
