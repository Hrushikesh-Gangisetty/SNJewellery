package com.snjewellery.admin.domain.net

import kotlinx.coroutines.flow.Flow

/**
 * Whether the phone has a network worth trying.
 *
 * ── What this deliberately does not promise ──────────────────────────
 * "Online" here means the system reports a validated network, not that
 * Supabase is reachable. A captive-portal Wi-Fi in a hotel, or a tower
 * that accepts the association and routes nothing, will both read as
 * online — so a sync that starts because of this can still fail, and
 * that is fine: the draft records the failure and waits for the next
 * signal.
 *
 * It exists to answer the cheaper question — *has anything changed since
 * the attempt that failed?* — because polling the server on a timer to
 * find out is exactly what a phone on mobile data must not do.
 */
interface ConnectivityMonitor {
    /**
     * Emits the current state immediately, then on every change.
     *
     * Distinct values only: a phone switching between two Wi-Fi access
     * points reports several networks and must not be read as several
     * reconnections.
     */
    val online: Flow<Boolean>
}
