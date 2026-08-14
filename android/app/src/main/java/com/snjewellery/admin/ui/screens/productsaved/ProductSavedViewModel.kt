package com.snjewellery.admin.ui.screens.productsaved

import androidx.lifecycle.ViewModel
import com.snjewellery.admin.domain.config.ConfigRepository
import com.snjewellery.admin.domain.config.WebsiteLinks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * The confirmation screen's one piece of state: whether this build knows
 * where the website is, and therefore whether it can offer to open the
 * piece there.
 *
 * A view model for a single synchronous read looks like ceremony, and it
 * is not: [ConfigRepository] is where the build system is confined
 * (ADR-0007, android-app.md §2.2), and a composable reaching it directly
 * would be the first place in the app that a screen read a data source.
 */
@HiltViewModel
class ProductSavedViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
) : ViewModel() {

    /**
     * The saved piece's address on the live site, or null when this build
     * was given no website — in which case the screen offers no such
     * button rather than one that opens a 404 on the shop's own site.
     */
    fun websiteUrl(slug: String): String? =
        configRepository.websiteUrl()?.let { WebsiteLinks.product(it, slug) }
}
