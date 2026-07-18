package com.medicineboxnotes.android

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.medicineboxnotes.designsystem.MBColor

private data class Tab(val route: String, @param:StringRes val label: Int, val icon: ImageVector)
private val tabs = listOf(
    Tab("home", R.string.tab_home, Icons.Rounded.Home), Tab("records", R.string.tab_records, Icons.Rounded.Description),
    Tab("medicines", R.string.tab_medicines, Icons.Rounded.Medication), Tab("query", R.string.tab_query, Icons.Rounded.Search),
    Tab("settings", R.string.tab_settings, Icons.Rounded.Settings),
)

@Composable
fun MedicineBoxApp(vm: MainViewModel, deepLinkedRecordId: String?) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route.orEmpty()
    Scaffold(
        containerColor = MBColor.Paper,
        bottomBar = {
            if (!current.startsWith("record/") && !current.startsWith("medicine/") && !current.startsWith("member/")) {
                Box(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = MBColor.Surface.copy(alpha = .98f),
                        border = BorderStroke(.5.dp, MBColor.Hairline),
                        shadowElevation = 3.dp,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 6.dp, vertical = 5.dp).selectableGroup(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            tabs.forEach { tab ->
                                val label = stringResource(tab.label)
                                NavigationDockItem(
                                    selected = current == tab.route,
                                    onClick = {
                                        nav.navigate(tab.route) {
                                            popUpTo(nav.graph.findStartDestination().id) {
                                                // Restoring the start destination's saved stack can reopen
                                                // the previously visited tab instead of showing Home.
                                                saveState = tab.route != "home"
                                            }
                                            launchSingleTop = true
                                            restoreState = tab.route != "home"
                                        }
                                    },
                                    icon = tab.icon,
                                    label = label,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = if (deepLinkedRecordId == null) "home" else "record/$deepLinkedRecordId",
            modifier = Modifier.padding(padding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable("home") { HomeScreen(vm, onRecords = { nav.navigate("records") }, onMedicines = { nav.navigate("medicines") }, onMember = { nav.navigate("member/$it") }) }
            composable("records") { RecordsScreen(vm, onRecord = { nav.navigate("record/$it") }) }
            composable("medicines") { MedicinesScreen(vm, onMedicine = { id -> nav.navigate("medicine/${id ?: "new"}") }) }
            composable("query") { QueryScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
            composable("record/{id}") { backStack ->
                RecordDetailScreen(vm, backStack.arguments?.getString("id").orEmpty(), onBack = nav::popBackStack)
            }
            composable("medicine/{id}") { backStack ->
                MedicineEditorScreen(vm, backStack.arguments?.getString("id").orEmpty().takeUnless { it == "new" }, onBack = nav::popBackStack)
            }
            composable("member/{id}") { backStack ->
                MemberDetailScreen(
                    vm = vm,
                    memberId = backStack.arguments?.getString("id").orEmpty(),
                    onBack = nav::popBackStack,
                    onRecord = { nav.navigate("record/$it") },
                )
            }
        }
    }
}

@Composable
private fun RowScope.NavigationDockItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    val iconColor by animateColorAsState(if (selected) MBColor.Ink else MBColor.Ink3, label = "dockIcon")
    val labelColor by animateColorAsState(if (selected) MBColor.Ink else MBColor.Ink3, label = "dockLabel")

    Column(
        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).selectable(
            selected = selected,
            role = Role.Tab,
            onClick = onClick,
        ).padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = labelColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = if (label.length > 10) 9.sp else 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                letterSpacing = 0.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
