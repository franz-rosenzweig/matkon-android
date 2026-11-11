package xyz.tiresia.matkon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import xyz.tiresia.matkon.data.Kitchen
import xyz.tiresia.matkon.timer.Timers

sealed interface Screen {
    data object Home : Screen

    data object Recipes : Screen

    data object Week : Screen

    data object Shop : Screen

    data object Settings : Screen

    data object Pantry : Screen

    data object History : Screen

    data class Recipe(val code: String) : Screen

    /** Cooking; `start` begins at that step (1 = from the top) instead of where it was left. */
    data class Cook(val code: String, val start: Int? = null) : Screen

    /** Fixing a recipe, in the language it's shown in. */
    data class Edit(val code: String, val lang: String) : Screen
}

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector)

private val TABS =
    listOf(
        Tab(Screen.Home, "Home", Icons.Default.Home),
        Tab(Screen.Recipes, "Recipes", Icons.Default.RestaurantMenu),
        Tab(Screen.Week, "Week", Icons.Default.CalendarMonth),
        Tab(Screen.Shop, "List", Icons.AutoMirrored.Filled.List),
    )

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val top get() = stack.last()

    /** The Recipes tab's creator filter (from @creator on a recipe). */
    var creator by mutableStateOf<String?>(null)

    /** Bumped when the current tab is tapped again: the screen scrolls back to the top. */
    var again by mutableStateOf(0)

    fun go(s: Screen) {
        if (top != s) stack.add(s)
    }

    fun tab(s: Screen) {
        if (stack.size == 1 && stack[0] == s) {
            again++
            return
        }
        stack.clear()
        stack.add(s)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun fromCreator(user: String) {
        creator = user
        stack.clear()
        stack.add(Screen.Recipes)
    }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("no Nav") }

/** A recipe a notification asked to open (a timer that went off). */
val opening = MutableStateFlow<String?>(null)

@Composable
fun MatkonRoot() {
    MatkonTheme {
        val signedIn by Kitchen.signedIn.collectAsStateWithLifecycle()
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (!signedIn) {
                SignInScreen()
            } else {
                KitchenFrame()
            }
        }
    }
}

@Composable
private fun KitchenFrame() {
    val nav = remember { Nav() }
    LifecycleResumeEffect(Unit) {
        Kitchen.refresh()
        onPauseOrDispose { }
    }
    val open by opening.collectAsStateWithLifecycle()
    LaunchedEffect(open) {
        open?.let {
            nav.go(Screen.Cook(it))
            opening.value = null
        }
    }
    val running by Timers.running.collectAsStateWithLifecycle()
    val ringing by Timers.ringing.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalNav provides nav) {
        BackHandler(enabled = nav.stack.size > 1) { nav.back() }
        val top = nav.top
        val full = top is Screen.Cook || top is Screen.Edit
        val wide = wide()
        val tabNow = nav.stack.first()
        Row(Modifier.fillMaxSize()) {
            if (wide && !full) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
                    Spacer(Modifier.weight(1f))
                    TABS.forEach { t ->
                        NavigationRailItem(
                            selected = tabNow == t.screen,
                            onClick = { nav.tab(t.screen) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    NavigationRailItem(
                        selected = tabNow == Screen.Settings,
                        onClick = { nav.tab(Screen.Settings) },
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("Settings") },
                    )
                }
            }
            Column(Modifier.weight(1f).fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (top) {
                        Screen.Home -> HomeScreen()
                        Screen.Recipes -> RecipesScreen()
                        Screen.Week -> WeekScreen()
                        Screen.Shop -> ShopScreen()
                        Screen.Settings -> SettingsScreen()
                        Screen.Pantry -> PantryScreen()
                        Screen.History -> HistoryScreen()
                        is Screen.Recipe -> RecipeScreen(top.code)
                        is Screen.Cook -> CookScreen(top.code, top.start)
                        is Screen.Edit -> EditScreen(top.code, top.lang)
                    }
                }
                if (!full) {
                    TimersBar(running, ringing, onOpen = { nav.go(Screen.Cook(it)) })
                }
                if (!wide && !full) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        TABS.forEach { t ->
                            NavigationBarItem(
                                selected = tabNow == t.screen,
                                onClick = { nav.tab(t.screen) },
                                icon = { Icon(t.icon, null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                } else if (!full) {
                    Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                }
            }
        }
        AddSheet()
    }
}
