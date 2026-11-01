package com.takeruf.nagi.testing

import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.*
import android.view.autofill.AutofillValue
import android.widget.RemoteViews

/** Debug-only provider: supplies synthetic credentials exclusively to the local test fixture. */
class AutofillHarnessService : AutofillService() {
    companion object {
        @Volatile var declineManualRequests = false
        @Volatile var offered = false
        @Volatile var fieldHints: Set<String> = emptySet()
        @Volatile var offerOnlyOnManualRequest = false
        @Volatile var manualRequests = 0
        @Volatile var automaticRequestsDeclined = 0
        const val LABEL = "Nagi fixture credentials"
    }

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val manual = request.flags and FillRequest.FLAG_MANUAL_REQUEST != 0
        if (manual) manualRequests++
        if (manual && declineManualRequests) {
            callback.onSuccess(null)
            return
        }
        if (offerOnlyOnManualRequest && !manual) {
            automaticRequestsDeclined++
            callback.onSuccess(null)
            return
        }
        val nodes = mutableListOf<AssistStructure.ViewNode>()
        fun visit(node: AssistStructure.ViewNode) {
            nodes.add(node)
            repeat(node.childCount) { visit(node.getChildAt(it)) }
        }
        val structure = request.fillContexts.last().structure
        repeat(structure.windowNodeCount) { visit(structure.getWindowNodeAt(it).rootViewNode) }
        if (nodes.none { it.webDomain == "127.0.0.1" }) {
            callback.onSuccess(null)
            return
        }
        fun field(id: String) = nodes.firstOrNull { node ->
            node.htmlInfo?.attributes?.any { it.first == "id" && it.second == id } == true
        }
        val username = field("login-user")
        val password = field("login-password")
        if (username?.autofillId == null) {
            callback.onSuccess(null)
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

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }
}
