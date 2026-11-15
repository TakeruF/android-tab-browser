package com.takeruf.nagi.testing

import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.*
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.takeruf.nagi.BuildConfig
import java.util.concurrent.CopyOnWriteArrayList

/** Debug-only synthetic provider. Default: loopback fixture; public sites require separate QA opt-in. */
class AutofillHarnessService : AutofillService() {
    data class LiveProbe(val allowedDomains: Set<String>, val offer: Boolean, val manualOnly: Boolean = false)
    data class LiveField(val kind: String, val hints: List<String>, val focused: Boolean,
        val width: Int, val height: Int, val hasAutofillId: Boolean)
    data class LiveObservation(val manual: Boolean, val domain: String?, val fields: List<LiveField>, val response: String)
    companion object {
        @Volatile var declineManualRequests = false
        @Volatile var offered = false
        @Volatile var fieldHints: Set<String> = emptySet()
        @Volatile var offerOnlyOnManualRequest = false
        @Volatile var manualRequests = 0
        @Volatile var automaticRequestsDeclined = 0
        @Volatile var focusedIdMatches = false
        @Volatile var fieldStructureValid = false
        @Volatile var totalRequests = 0
        @Volatile var nullResponses = 0
        const val LABEL = "Nagi fixture credentials"
        // Explicit opt-in for anonymous public login pages on the separate diagnostic install.
        @Volatile var liveProbe: LiveProbe? = null
        val liveObservations = CopyOnWriteArrayList<LiveObservation>()
        const val LIVE_LABEL = "Nagi synthetic live-site credentials"
        const val LIVE_USERNAME = "nagi-autofill-test@example.invalid"
        const val LIVE_PASSWORD = "Nagi-Synthetic-Only-2026!"
    }

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        totalRequests++
        fun noMatches() { nullResponses++; callback.onSuccess(null) }
        val manual = request.flags and FillRequest.FLAG_MANUAL_REQUEST != 0
        if (manual) manualRequests++
        liveProbe?.let { probe ->
            if (android.os.Build.VERSION.SDK_INT >= 30) fillLiveProbe(request, callback, probe, manual)
            else noMatches()
            return
        }
        if (manual && declineManualRequests) {
            noMatches()
            return
        }
        if (offerOnlyOnManualRequest && !manual) {
            automaticRequestsDeclined++
            noMatches()
            return
        }
        val nodes = mutableListOf<AssistStructure.ViewNode>()
        fun visit(node: AssistStructure.ViewNode) {
            nodes.add(node)
            repeat(node.childCount) { visit(node.getChildAt(it)) }
        }
        val structure = request.fillContexts.last().structure
        repeat(structure.windowNodeCount) { visit(structure.getWindowNodeAt(it).rootViewNode) }
        val focusedId = if (android.os.Build.VERSION.SDK_INT >= 29) request.fillContexts.last().focusedId
            else nodes.firstOrNull { it.isFocused }?.autofillId
        // Split can expose several WebViews, even with identical resource IDs.
        // Offer only fields below the loopback form owning the request's AutofillId.
        fun containsFocus(node: AssistStructure.ViewNode): Boolean =
            node.autofillId == focusedId ||
                (0 until node.childCount).any { containsFocus(node.getChildAt(it)) }
        val form = nodes.firstOrNull { it.webDomain == "127.0.0.1" && containsFocus(it) }
        if (form == null) {
            noMatches()
            return
        }
        nodes.clear()
        visit(form)
        fun field(id: String) = nodes.firstOrNull { node ->
            node.htmlInfo?.attributes?.any { it.first == "id" && it.second == id } == true
        }
        val username = field("login-user")
        val password = field("login-password")
        focusedIdMatches = nodes.any { it.isFocused && it.autofillId == focusedId }
        fieldStructureValid = listOfNotNull(username, password).all {
            it.autofillType == android.view.View.AUTOFILL_TYPE_TEXT && it.width > 0 && it.height > 0
        } && (password == null || username?.autofillId != password.autofillId)
        if (username?.autofillId == null) {
            noMatches()
            return
        }
        fieldHints = (username.autofillHints.orEmpty().toList() + password?.autofillHints.orEmpty().toList()).toSet()
        val presentation = RemoteViews("android", android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, LABEL)
        }
        @Suppress("DEPRECATION")
        val builder = Dataset.Builder(presentation)
            .setValue(username.autofillId!!, AutofillValue.forText("fixture-user"))
        password?.autofillId?.let { builder.setValue(it, AutofillValue.forText("fixture-password")) }
        val dataset = builder.build()
        offered = true
        callback.onSuccess(FillResponse.Builder().addDataset(dataset).build())
    }

    @androidx.annotation.RequiresApi(30)
    private fun fillLiveProbe(request: FillRequest, callback: FillCallback, probe: LiveProbe, manual: Boolean) {
        val structure = request.fillContexts.last().structure
        if (!BuildConfig.PASSWORD_AUTOFILL_DIAGNOSTICS ||
            !BuildConfig.APPLICATION_ID.endsWith(".autofillqa") ||
            structure.activityComponent.packageName != BuildConfig.APPLICATION_ID) {
            callback.onSuccess(null)
            return
        }
        val nodes = mutableListOf<AssistStructure.ViewNode>()
        fun visit(node: AssistStructure.ViewNode) {
            nodes.add(node)
            repeat(node.childCount) { visit(node.getChildAt(it)) }
        }
        repeat(structure.windowNodeCount) { visit(structure.getWindowNodeAt(it).rootViewNode) }
        val focusedId = request.fillContexts.last().focusedId
        fun containsFocus(node: AssistStructure.ViewNode): Boolean = node.autofillId == focusedId ||
            (0 until node.childCount).any { containsFocus(node.getChildAt(it)) }
        val form = nodes.firstOrNull { it.webDomain in probe.allowedDomains && containsFocus(it) }
        if (form == null) {
            liveObservations.add(LiveObservation(manual, null, emptyList(), "outside_allowlist"))
            callback.onSuccess(null)
            return
        }
        nodes.clear(); visit(form)
        fun kind(node: AssistStructure.ViewNode): String? {
            if (node.autofillType != android.view.View.AUTOFILL_TYPE_TEXT ||
                node.visibility != android.view.View.VISIBLE || node.width <= 0 || node.height <= 0) return null
            val type = node.htmlInfo?.attributes?.firstOrNull { it.first == "type" }?.second?.lowercase()
            return when (type) {
                "password" -> "password"
                "text", "email", "tel" -> "username"
                else -> null
            }
        }
        val fields = nodes.mapNotNull { node -> kind(node)?.let { it to node } }
        val facts = fields.map { (kind, node) ->
            LiveField(kind, node.autofillHints.orEmpty().filter {
                it in setOf("username", "email", "emailAddress", "current-password", "password", "new-password", "off", "on")
            }, node.autofillId == focusedId, node.width, node.height, node.autofillId != null)
        }
        val username = fields.firstOrNull { it.first == "username" }?.second
        val password = fields.firstOrNull { it.first == "password" }?.second
        val response = when {
            username?.autofillId == null && password?.autofillId == null -> "no_fields"
            !probe.offer -> "none"
            probe.manualOnly && !manual -> "manual_only"
            else -> "dataset"
        }
        liveObservations.add(LiveObservation(manual, form.webDomain, facts, response))
        if (response != "dataset") { callback.onSuccess(null); return }
        val presentation = RemoteViews("android", android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, LIVE_LABEL)
        }
        @Suppress("DEPRECATION")
        val builder = Dataset.Builder(presentation)
        username?.autofillId?.let { builder.setValue(it, AutofillValue.forText(LIVE_USERNAME)) }
        password?.autofillId?.let { builder.setValue(it, AutofillValue.forText(LIVE_PASSWORD)) }
        callback.onSuccess(FillResponse.Builder().addDataset(builder.build()).build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }
}
