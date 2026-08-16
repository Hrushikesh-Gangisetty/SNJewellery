package com.snjewellery.admin.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.snjewellery.admin.ui.screens.addproduct.AddProductScreen
import com.snjewellery.admin.ui.screens.catalogue.CatalogueScreen
import com.snjewellery.admin.ui.screens.categories.CategoriesScreen
import com.snjewellery.admin.ui.screens.dashboard.DashboardScreen
import com.snjewellery.admin.ui.screens.productsaved.ProductSavedScreen

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
                onViewCatalogue = { navController.navigate(Catalogue) },
                onManageCategories = { navController.navigate(Categories) },
            )
        }

        composable<Categories> {
            CategoriesScreen(onBack = { navController.popBackStack() })
        }

        composable<Catalogue> {
            CatalogueScreen(
                onBack = { navController.popBackStack() },
                onEdit = { productId -> navController.navigate(EditProduct(productId)) },
            )
        }

        // The same screen as AddProduct. Its view model reads the id off
        // the route and fills the form; everything else is identical,
        // which is the point of not having written a second form.
        composable<EditProduct> {
            AddProductScreen(
                // Editing writes no new row, so there is no confirmation
                // to show — the list the owner came from is where the
                // change is visible, and it reloads on resume.
                onSaved = { _, _ -> navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable<AddProduct> {
            AddProductScreen(
                onSaved = { name, slug ->
                    navController.navigate(ProductSaved(name, slug)) {
                        // The form is left behind, not stacked under the
                        // confirmation. Its Save button stays live and its
                        // attempt record has been cleared, so a Back into
                        // it followed by a tap would enter the same piece
                        // a second time. Popping it is a correctness
                        // requirement, not tidiness.
                        popUpTo(AddProduct) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<ProductSaved> { entry ->
            val saved = entry.toRoute<ProductSaved>()
            ProductSavedScreen(
                name = saved.name,
                slug = saved.slug,
                // A fresh back stack entry, so the next form arrives with
                // an empty SavedStateHandle and nothing of the last piece.
                onAddAnother = {
                    navController.navigate(AddProduct) {
                        popUpTo(Dashboard)
                    }
                },
                // The dashboard reloads on resume, so its counts include
                // the piece just saved.
                onDone = { navController.popBackStack() },
            )
        }
    }
}
