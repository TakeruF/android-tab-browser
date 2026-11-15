package com.takeruf.nagi.browser.engine

import android.view.View
import android.view.autofill.AutofillManager

/** AutofillManager has one callback slot per Activity, shared by all live tabs. Main thread only. */
internal object WebViewAutofillCallbacks {
    private class Dispatcher : AutofillManager.AutofillCallback() {
        val targets = mutableMapOf<View, (Int, Int) -> Unit>()
        override fun onAutofillEvent(view: View, virtualId: Int, event: Int) {
            val diagnosticEvent = when (event) {
                EVENT_INPUT_SHOWN -> PasswordAutofillDiagnostics.Event.UI_SHOWN
                EVENT_INPUT_HIDDEN -> PasswordAutofillDiagnostics.Event.UI_HIDDEN
                EVENT_INPUT_UNAVAILABLE -> PasswordAutofillDiagnostics.Event.UI_UNAVAILABLE
                else -> null
            }
            if (view in targets && diagnosticEvent != null) PasswordAutofillDiagnostics.record(diagnosticEvent)
            if (event == EVENT_INPUT_SHOWN || event == EVENT_INPUT_UNAVAILABLE) targets[view]?.invoke(virtualId, event)
        }
    }
    private val dispatchers = mutableMapOf<AutofillManager, Dispatcher>()

    fun register(manager: AutofillManager, view: View, onField: (Int, Int) -> Unit) {
        val dispatcher = dispatchers.getOrPut(manager) { Dispatcher() }
        dispatcher.targets[view] = onField
        // Constructing a WebView installs its own UI monitor in the same slot.
        // Reattach the shared dispatcher after every new engine is initialized.
        manager.registerCallback(dispatcher)
    }

    fun unregister(manager: AutofillManager, view: View) {
        val dispatcher = dispatchers[manager] ?: return
        dispatcher.targets.remove(view)
        if (dispatcher.targets.isEmpty()) {
            manager.unregisterCallback(dispatcher)
            dispatchers.remove(manager)
        }
    }
}
