package com.takeruf.nagi.browser.engine

import com.takeruf.nagi.BuildConfig

/** Keep production autofill unavailable until the submitted approval is granted. */
object PasswordAutofillAvailability {
    const val approved = false
    val enabled: Boolean get() = approved || (BuildConfig.DEBUG && BuildConfig.PASSWORD_AUTOFILL_DIAGNOSTICS)
}
