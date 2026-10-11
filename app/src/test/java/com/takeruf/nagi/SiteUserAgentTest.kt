package com.takeruf.nagi

import com.takeruf.nagi.browser.engine.SiteUserAgent
import org.junit.Assert.*
import org.junit.Test

class SiteUserAgentTest {
    private val tablet = "Mozilla/5.0 (Linux; Android 16; Tablet; wv) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Version/4.0 Chrome/133.0.6943.137 Safari/537.36"

    @Test fun tabletRequestsPhoneLayoutWithoutChangingTheInstalledBrowserVersion() {
        val phone = SiteUserAgent.mobile(tablet)
        assertEquals(tablet.replace(" Safari/", " Mobile Safari/"), phone)
        assertEquals(phone, SiteUserAgent.mobile(phone))
    }

    @Test fun phoneDefaultIsPreserved() {
        val phone = tablet.replace(" Safari/", " Mobile Safari/")
        assertEquals(phone, SiteUserAgent.mobile(phone))
    }
}
