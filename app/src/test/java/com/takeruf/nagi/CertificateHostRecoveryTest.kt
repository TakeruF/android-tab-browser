package com.takeruf.nagi

import com.takeruf.nagi.browser.engine.CertificateHostRecovery
import org.junit.Assert.*
import org.junit.Test

class CertificateHostRecoveryTest {
    private val wwwOnly = listOf("www.afpbb.com")

    @Test fun mainDocumentMatchingAcceptsWebViewsCanonicalRoot() {
        assertTrue(CertificateHostRecovery.matchesMainDocument("https://afpbb.com/", "https://afpbb.com"))
        assertTrue(CertificateHostRecovery.matchesMainDocument("https://afpbb.com/", "https://AFPBB.com:443/#top"))
        assertTrue(CertificateHostRecovery.matchesMainDocument("https://afpbb.com/news/a%2Fb?q=%E5%A4%A7", "https://afpbb.com/news/a%2Fb?q=%E5%A4%A7#top"))
    }

    @Test fun canonicalRootMatchingStillExcludesOtherPagesAndOrigins() {
        for (error in listOf("https://www.afpbb.com/", "http://afpbb.com/", "https://afpbb.com:444/",
            "https://afpbb.com/image.jpg", "https://afpbb.com/?other=1", "https://user@afpbb.com/", "not a url")) {
            assertFalse(error, CertificateHostRecovery.matchesMainDocument(error, "https://afpbb.com"))
        }
        assertFalse(CertificateHostRecovery.matchesMainDocument("https://afpbb.com", "invalid"))
    }

    @Test fun movesBareHostToWwwWhenTheCertificateNamesWww() {
        assertEquals("https://www.afpbb.com/", CertificateHostRecovery.wwwRetryUrl("https://afpbb.com", wwwOnly))
        assertEquals("https://www.afpbb.com/", CertificateHostRecovery.wwwRetryUrl("https://AFPBB.com/", listOf("WWW.AFPBB.COM.")))
        assertEquals("https://www.example.com/", CertificateHostRecovery.wwwRetryUrl("https://example.com/", listOf("*.example.com")))
    }

    @Test fun keepsPortPathQueryAndFragmentEscapes() {
        assertEquals("https://www.afpbb.com:8443/news/a%2Fb?q=%E5%A4%A7&x=1#top",
            CertificateHostRecovery.wwwRetryUrl("https://afpbb.com:8443/news/a%2Fb?q=%E5%A4%A7&x=1#top", wwwOnly))
    }

    @Test fun refusesWhenTheCertificateDoesNotProveTheWwwHost() {
        for (names in listOf(emptyList(), listOf("afpbb.com"), listOf("www.evil.example"), listOf("*.com"),
            listOf("www.afpbb.com.evil.example"), listOf("*.www.afpbb.com"))) {
            assertNull(names.toString(), CertificateHostRecovery.wwwRetryUrl("https://afpbb.com/", names))
        }
    }

    @Test fun refusesUrlsThatAreNotBareHttpsHosts() {
        for (url in listOf("http://afpbb.com/", "https://www.afpbb.com/", "https://localhost/", "https://192.168.0.1/",
            "https://[::1]/", "https://user@afpbb.com/", "not a url")) {
            assertNull(url, CertificateHostRecovery.wwwRetryUrl(url, listOf("www.afpbb.com", "www.localhost", "www.192.168.0.1")))
        }
    }
}
