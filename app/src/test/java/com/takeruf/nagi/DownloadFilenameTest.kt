package com.takeruf.nagi

import com.takeruf.nagi.browser.downloads.DownloadFilename
import org.junit.Assert.*
import org.junit.Test

class DownloadFilenameTest {
    @Test fun preservesFilenameInsteadOfRewritingExtensionFromGenericMimeType() {
        assertEquals("orbit.txt", DownloadFilename.fromDisposition("attachment; filename=orbit.txt"))
        assertEquals("two words.pdf", DownloadFilename.fromDisposition("attachment; filename=\"two words.pdf\""))
    }
    @Test fun handlesUtf8NamesAndLiteralPlus() {
        assertEquals("日本語+中文.txt", DownloadFilename.fromDisposition("attachment; filename*=UTF-8''%E6%97%A5%E6%9C%AC%E8%AA%9E+%E4%B8%AD%E6%96%87.txt"))
    }
    @Test fun absentOrEmptyFilenameFallsBackToUrl() {
        assertNull(DownloadFilename.fromDisposition(null))
        assertNull(DownloadFilename.fromDisposition("attachment; filename=\"\""))
    }
}
