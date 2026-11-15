package com.takeruf.nagi.browser.engine

import android.util.Log
import com.takeruf.nagi.BuildConfig

/** Opt-in QA metadata only. Never accepts field values, URLs, IDs, or provider payloads. */
internal object PasswordAutofillDiagnostics {
    enum class Event {
        ENGINE_ENABLED, STRUCTURE_REQUESTED, FOCUSED_FIELD_OBSERVED, MANUAL_REQUEST_CALLED,
        SERVICE_DISABLED, WINDOW_NOT_READY, NO_TEXT_EDITOR, NO_FOCUSED_ID,
        UI_SHOWN, UI_HIDDEN, UI_UNAVAILABLE, FILL_DELIVERED,
    }

    fun record(event: Event, fieldCount: Int? = null) {
        if (BuildConfig.DEBUG && BuildConfig.PASSWORD_AUTOFILL_DIAGNOSTICS) {
            Log.i("NagiAutofill", "event=${event.name}" + (fieldCount?.let { " fieldCount=$it" } ?: ""))
        }
    }
}
