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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bulkplan.tracker.ui.C
import com.bulkplan.tracker.ui.Ico
import com.bulkplan.tracker.ui.PageHeader
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

private data class Tab(val key: String, val label: String, val title: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("food", "Food", "Nutrition Tracker", Ico.Fork),
    Tab("progress", "Progress", "Progress", Ico.Chart),
    Tab("today", "Today", "Today’s Schedule", Ico.Calendar),
    Tab("workout", "Workout", "Workout", Ico.Dumbbell),
    Tab("coach", "Coach", "AI Coach", Ico.Chat),
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
        val sub = PlanLogic.fmtD(t) + " · " + when {
            w < 1 -> "Plan starts ${PlanLogic.fmtD(state.plan.startDate)}"
            w > state.plan.weeks -> "Plan complete"
            else -> "Week $w of ${state.plan.weeks}" + (b?.let { " · ${it.name}" } ?: "")
        }
        val current = TABS.first { it.key == tab }
        Scaffold(
            containerColor = C.Bg,
            snackbarHost = { SnackbarHost(snack) },
            topBar = { Box(Modifier.statusBarsPadding()) { PageHeader(current.title, current.icon, sub) { settings = true } } },
            bottomBar = { BottomBar(tab) { tab = it } },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    "today" -> TodayScreen(onGo = { tab = it })
                    "food" -> FoodScreen()
                    "workout" -> WorkoutScreen()
                    "progress" -> ProgressScreen()
                    "coach" -> CoachScreen(onOpenSettings = { settings = true }, onGo = { tab = it })
                }
            }
        }
    }
}

/** Bottom bar with a raised round button in the middle for Today, as in the mockups. */
@Composable
private fun BottomBar(tab: String, onPick: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().background(C.Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp).background(C.Card).navigationBarsPadding().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEach { tb ->
                val sel = tb.key == tab
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { onPick(tb.key) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    if (tb.key != "today") {
                        Icon(tb.icon, tb.label, tint = if (sel) C.Blue else C.Muted, modifier = Modifier.size(26.dp))
                        Text(tb.label, fontSize = 12.sp, color = if (sel) C.Blue else C.Muted)
                    } else Spacer(Modifier.height(30.dp))
                }
            }
        }
        Box(
            Modifier.align(Alignment.TopCenter).size(64.dp).clip(CircleShape)
                .background(if (tab == "today") C.Blue else C.BlueTint)
                .border(4.dp, C.Bg, CircleShape)
                .clickable { onPick("today") },
            contentAlignment = Alignment.Center,
        ) { Icon(Ico.Calendar, "Today", tint = Color.White, modifier = Modifier.size(30.dp)) }
    }
}
