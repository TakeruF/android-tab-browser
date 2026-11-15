package com.takeruf.nagi

import com.takeruf.nagi.ui.settings.LicenseCatalog
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LicenseCatalogTest {
    private val directory = File("src/main/assets/${LicenseCatalog.DIRECTORY}")

    @Test fun everyListedLicenseFileIsBundled() {
        val listed = LicenseCatalog.entries.flatMap { it.files } + LicenseCatalog.INVENTORY
        for (file in listed) assertTrue("missing $file", File(directory, file).isFile)
    }

    @Test fun everyBundledLicenseTextIsReachableFromTheScreen() {
        val listed = (LicenseCatalog.entries.flatMap { it.files } + LicenseCatalog.INVENTORY).toSet()
        val bundled = directory.listFiles { file -> file.isFile && file.extension == "txt" }!!.map { it.name }
        for (file in bundled) assertTrue("$file is not shown in Open-source licenses", file in listed)
    }

    @Test fun entriesWithoutBundledTextsPointToTheirSource() {
        for (entry in LicenseCatalog.entries.filter { it.files.isEmpty() })
            assertTrue("${entry.name} has neither a text nor a source", !entry.source.isNullOrBlank())
    }
}
