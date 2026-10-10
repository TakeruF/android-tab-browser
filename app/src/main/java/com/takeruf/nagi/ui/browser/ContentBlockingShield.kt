package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.R
import com.takeruf.nagi.browser.engine.PageState
import com.takeruf.nagi.browser.engine.SiteDisplayModeStore
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.ui.components.*
import com.takeruf.nagi.ui.localization.rememberNagiStrings

@Composable
fun ContentBlockingShield(page: PageState, settings: BrowserSettings, onFocus: () -> Unit,
    onPauseVisit: (Boolean) -> Unit, onSiteBlockingChange: (String, Boolean) -> Unit) {
    val strings = rememberNagiStrings()
    val host = SiteDisplayModeStore.siteHost(page.url)
    var expanded by remember(host) { mutableStateOf(false) }
    val excluded = host in settings.adBlockExcludedHosts
    val paused = page.visitBlockingExceptionSite != null && page.visitBlockingExceptionSite == com.takeruf.nagi.browser.blocking.blockingSite(page.url)
    val active = settings.adBlockingEnabled && !excluded && !paused
    val status = strings(when {
        !settings.adBlockingEnabled -> R.string.ui_blocking_disabled_global
        excluded -> R.string.ui_blocking_disabled_site
        paused -> R.string.ui_blocking_paused_visit
        else -> R.string.ui_blocking_active
    })
    Box {
        AddressActionButton(enabled = host != null, modifier = Modifier.testTag("ad-blocking-shield"),
            onClick = { onFocus(); expanded = true }) {
            // The shield's narrower outline needs a small optical size correction
            // beside the 16dp link/share glyphs. The action button stays unchanged.
            Icon(if (active) NagiIcons.ShieldCheck else NagiIcons.ShieldOff,
                "${strings(R.string.ui_ad_blocking)}, $status", Modifier.size(18.dp))
        }
        NagiOverflowMenu(expanded, { expanded = false }, Modifier.testTag("ad-blocking-popup")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(host.orEmpty(), style = MaterialTheme.typography.titleSmall)
                Text(status, style = MaterialTheme.typography.bodySmall)
                Text(strings(R.string.ui_blocking_count, page.blockedRequests),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            NagiOverflowMenuDivider()
            // A persistent exception takes precedence; the visit action is for sites normally blocked.
            if (!excluded) {
                NagiOverflowMenuItem(
                    text = { Text(strings(if (paused) R.string.ui_blocking_resume_visit else R.string.ui_blocking_pause_visit)) },
                    leadingIcon = { Icon(if (paused) NagiIcons.ShieldCheck else NagiIcons.ShieldOff, null) },
                    enabled = host != null && settings.adBlockingEnabled,
                    modifier = Modifier.testTag("blocking-visit-toggle"),
                    onClick = { expanded = false; onPauseVisit(!paused) })
                Text(strings(R.string.ui_blocking_visit_scope), Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            NagiOverflowMenuItem(
                text = { Text(strings(if (excluded) R.string.ui_blocking_always_enable else R.string.ui_blocking_always_disable)) },
                leadingIcon = { Icon(if (excluded) NagiIcons.ShieldCheck else NagiIcons.ShieldOff, null) },
                enabled = host != null && settings.adBlockingEnabled,
                modifier = Modifier.testTag("blocking-site-toggle"),
                onClick = { expanded = false; host?.let { onSiteBlockingChange(it, excluded) } })
        }
    }
}
