package com.takeruf.nagi

import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.testing.InputConnectionHarnessIme
import android.view.inputmethod.InputConnection
import android.view.inputmethod.EditorInfo
import android.text.InputType

class TestIme : AutoCloseable {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val original = Settings.Secure.getString(instrumentation.targetContext.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    init {
        shell("ime enable com.takeruf.nagi/.testing.InputConnectionHarnessIme")
        shell("ime set com.takeruf.nagi/.testing.InputConnectionHarnessIme")
    }
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    fun awaitBinding() {
        val deadline = System.currentTimeMillis() + 10_000
        while ((!InputConnectionHarnessIme.bound || InputConnectionHarnessIme.editor == null) &&
            System.currentTimeMillis() < deadline) Thread.sleep(25)
        check(InputConnectionHarnessIme.bound && InputConnectionHarnessIme.editor != null) { "The system did not bind the test IME" }
    }
    fun connection(viewId: Int? = null, action: Int? = null): InputConnection {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            InputConnectionHarnessIme.active?.let {
                val editor = InputConnectionHarnessIme.editor
                if (editor != null && editor.inputType != InputType.TYPE_NULL &&
                    (viewId == null || editor.fieldId == viewId) &&
                    (action == null || editor.imeOptions and EditorInfo.IME_MASK_ACTION == action)) return it
            }
            Thread.sleep(25)
        }
        error("The system did not connect the test IME to editor $viewId / action $action; current=${InputConnectionHarnessIme.editor?.fieldId}, type=${InputConnectionHarnessIme.editor?.inputType}, options=${InputConnectionHarnessIme.editor?.imeOptions}")
    }
    override fun close() {
        if (original != null) {
            shell("ime set $original")
            // `ime set` returns before the old service is unbound. Do not let a following
            // test switch back while the previous IME switch is still in flight.
            val deadline = System.currentTimeMillis() + 10_000
            while (InputConnectionHarnessIme.bound && System.currentTimeMillis() < deadline) Thread.sleep(25)
            check(!InputConnectionHarnessIme.bound) { "The system did not unbind the test IME" }
        }
    }
}
