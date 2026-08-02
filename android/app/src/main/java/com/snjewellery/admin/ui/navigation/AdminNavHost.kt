package com.snjewellery.admin.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.snjewellery.admin.ui.screens.addproduct.AddProductScreen
import com.snjewellery.admin.ui.screens.dashboard.DashboardScreen

/**
 * The authenticated app's navigation graph.
 *
 * Reached only from `SessionState.Admin`, so nothing inside it has to ask
 * again whether the person is signed in or allowed in — that question is
 * answered once, above (M6.9).
 *
 * ── Why the back stack is not asked to hold sign-in ──────────────────
 * Logging out does not pop anything here; it clears the session, and the
 * state above this swaps the whole graph for the login screen. That is
 * what makes "relaunch after logout shows sign-in" true for free: there
 * is no navigation state to get out of step with the session, because
 * the session is the only thing deciding.
 */
@Composable
fun AdminNavHost(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Dashboard,
        modifier = modifier,
    ) {
        composable<Dashboard> {
            DashboardScreen(
                onSignOut = onSignOut,
                onAddProduct = { navController.navigate(AddProduct) },
            )
        }

        composable<AddProduct> {
            AddProductScreen(
                // The dashboard reloads on resume, so popping back shows
                // a total that includes what was just saved. M7.12
                // replaces this with a confirmation that links to the
                // new product; returning to updated counts is the
                // honest minimum until then.
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
