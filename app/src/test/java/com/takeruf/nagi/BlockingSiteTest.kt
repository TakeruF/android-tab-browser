package com.takeruf.nagi

import com.takeruf.nagi.browser.blocking.blockingSite
import org.junit.Assert.*
import org.junit.Test

class BlockingSiteTest {
    @Test fun articlesAndSubdomainsShareQqButLookalikesDoNot() {
        for (url in listOf("https://qq.com", "https://news.qq.com/a/123", "http://sports.QQ.com.:8080/article"))
            assertEquals("qq.com", blockingSite(url))
        assertEquals("evil.com", blockingSite("https://qq.com.evil.com/article"))
        assertEquals("evilqq.com", blockingSite("https://evilqq.com/article"))
    }
    @Test fun multiLabelAndPrivateSuffixesSeparateUnrelatedSites() {
        assertEquals("example.co.jp", blockingSite("https://news.example.co.jp/article"))
        assertEquals("example.co.uk", blockingSite("https://www.example.co.uk/article"))
        assertEquals("alice.github.io", blockingSite("https://news.alice.github.io/article"))
        assertEquals("bob.github.io", blockingSite("https://bob.github.io/article"))
    }
    @Test fun loopbackAndNonWebAddressesDoNotGainAnUnrelatedScope() {
        assertEquals("127.0.0.1", blockingSite("http://127.0.0.1:8000/article"))
        assertEquals("::1", blockingSite("http://[::1]:8000/article"))
        assertEquals("localhost", blockingSite("http://localhost/article"))
        assertNull(blockingSite("about:blank"))
        assertNull(blockingSite("javascript:alert(1)"))
        assertNull(blockingSite(""))
    }
}
