package com.takeruf.nagi.browser.engine;

import android.view.ViewStructure;

/** Observes native focused field IDs while forwarding the original structure unchanged.
 * Values, HTML attributes, and text are never retained here. UI callbacks are optional:
 * providers can silently decline automatic suggestions without reporting a virtual ID.
 */
@android.annotation.SuppressLint("NewApi")
final class FocusedAutofillStructure extends ViewStructure {
    interface FocusedField { void accept(int virtualId); }
    private final ViewStructure delegate;
    private final FocusedField onField;
    private Integer virtualId;
    private boolean focused;
    FocusedAutofillStructure(ViewStructure delegate, FocusedField onField) {
        this.delegate = delegate;
        this.onField = onField;
    }
    private void reportFocus() { if (focused && virtualId != null) onField.accept(virtualId); }
    @Override public int addChildCount(int p0) { return delegate.addChildCount(p0); }
    @Override public void asyncCommit() { delegate.asyncCommit(); }
    @Override public android.view.ViewStructure asyncNewChild(int p0) { return new FocusedAutofillStructure(delegate.asyncNewChild(p0), onField); }
    @Override public android.view.autofill.AutofillId getAutofillId() { return delegate.getAutofillId(); }
    @Override public int getChildCount() { return delegate.getChildCount(); }
    @Override public android.os.Bundle getExtras() { return delegate.getExtras(); }
    @Override public java.lang.CharSequence getHint() { return delegate.getHint(); }
    @Override public java.lang.CharSequence getText() { return delegate.getText(); }
    @Override public int getTextSelectionEnd() { return delegate.getTextSelectionEnd(); }
    @Override public int getTextSelectionStart() { return delegate.getTextSelectionStart(); }
    @Override public boolean hasExtras() { return delegate.hasExtras(); }
    @Override public android.view.ViewStructure newChild(int p0) { return new FocusedAutofillStructure(delegate.newChild(p0), onField); }
    @Override public android.view.ViewStructure.HtmlInfo.Builder newHtmlInfoBuilder(java.lang.String p0) { return delegate.newHtmlInfoBuilder(p0); }
    @Override public void setAccessibilityFocused(boolean p0) { delegate.setAccessibilityFocused(p0); }
    @Override public void setActivated(boolean p0) { delegate.setActivated(p0); }
    @Override public void setAlpha(float p0) { delegate.setAlpha(p0); }
    @Override public void setAutofillHints(java.lang.String[] p0) { delegate.setAutofillHints(p0); }
    @Override public void setAutofillId(android.view.autofill.AutofillId p0) { delegate.setAutofillId(p0); }
    @Override public void setAutofillId(android.view.autofill.AutofillId p0, int p1) { delegate.setAutofillId(p0, p1); virtualId = p1; reportFocus(); }
    @Override public void setAutofillOptions(java.lang.CharSequence[] p0) { delegate.setAutofillOptions(p0); }
    @Override public void setAutofillType(int p0) { delegate.setAutofillType(p0); }
    @Override public void setAutofillValue(android.view.autofill.AutofillValue p0) { delegate.setAutofillValue(p0); }
    @Override public void setCheckable(boolean p0) { delegate.setCheckable(p0); }
    @Override public void setChecked(boolean p0) { delegate.setChecked(p0); }
    @Override public void setChildCount(int p0) { delegate.setChildCount(p0); }
    @Override public void setClassName(java.lang.String p0) { delegate.setClassName(p0); }
    @Override public void setClickable(boolean p0) { delegate.setClickable(p0); }
    @Override public void setContentDescription(java.lang.CharSequence p0) { delegate.setContentDescription(p0); }
    @Override public void setContextClickable(boolean p0) { delegate.setContextClickable(p0); }
    @Override public void setDataIsSensitive(boolean p0) { delegate.setDataIsSensitive(p0); }
    @Override public void setDimens(int p0, int p1, int p2, int p3, int p4, int p5) { delegate.setDimens(p0, p1, p2, p3, p4, p5); }
    @Override public void setElevation(float p0) { delegate.setElevation(p0); }
    @Override public void setEnabled(boolean p0) { delegate.setEnabled(p0); }
    @Override public void setFocusable(boolean p0) { delegate.setFocusable(p0); }
    @Override public void setFocused(boolean p0) { delegate.setFocused(p0); focused = p0; reportFocus(); }
    @Override public void setHint(java.lang.CharSequence p0) { delegate.setHint(p0); }
    @Override public void setHtmlInfo(android.view.ViewStructure.HtmlInfo p0) { delegate.setHtmlInfo(p0); }
    @Override public void setId(int p0, java.lang.String p1, java.lang.String p2, java.lang.String p3) { delegate.setId(p0, p1, p2, p3); }
    @Override public void setInputType(int p0) { delegate.setInputType(p0); }
    @Override public void setLocaleList(android.os.LocaleList p0) { delegate.setLocaleList(p0); }
    @Override public void setLongClickable(boolean p0) { delegate.setLongClickable(p0); }
    @Override public void setOpaque(boolean p0) { delegate.setOpaque(p0); }
    @Override public void setSelected(boolean p0) { delegate.setSelected(p0); }
    @Override public void setText(java.lang.CharSequence p0) { delegate.setText(p0); }
    @Override public void setText(java.lang.CharSequence p0, int p1, int p2) { delegate.setText(p0, p1, p2); }
    @Override public void setTextLines(int[] p0, int[] p1) { delegate.setTextLines(p0, p1); }
    @Override public void setTextStyle(float p0, int p1, int p2, int p3) { delegate.setTextStyle(p0, p1, p2, p3); }
    @Override public void setTransformation(android.graphics.Matrix p0) { delegate.setTransformation(p0); }
    @Override public void setVisibility(int p0) { delegate.setVisibility(p0); }
    @Override public void setWebDomain(java.lang.String p0) { delegate.setWebDomain(p0); }
    // Optional platform methods must also pass through (including credential requests).
    // They are called by WebView only on platform versions that provide those methods.
    @Override public void clearCredentialManagerRequest() { delegate.clearCredentialManagerRequest(); }
    @Override public android.credentials.GetCredentialRequest getPendingCredentialRequest() { return delegate.getPendingCredentialRequest(); }
    @Override public void setHintIdEntry(java.lang.String p0) { delegate.setHintIdEntry(p0); }
    @Override public void setImportantForAutofill(int p0) { delegate.setImportantForAutofill(p0); }
    @Override public void setMaxTextEms(int p0) { delegate.setMaxTextEms(p0); }
    @Override public void setMaxTextLength(int p0) { delegate.setMaxTextLength(p0); }
    @Override public void setMinTextEms(int p0) { delegate.setMinTextEms(p0); }
    @Override public void setPendingCredentialRequest(android.credentials.GetCredentialRequest p0, android.os.OutcomeReceiver<android.credentials.GetCredentialResponse, android.credentials.GetCredentialException> p1) { delegate.setPendingCredentialRequest(p0, p1); }
    @Override public void setReceiveContentMimeTypes(java.lang.String[] p0) { delegate.setReceiveContentMimeTypes(p0); }
    @Override public void setTextIdEntry(java.lang.String p0) { delegate.setTextIdEntry(p0); }
    @Override public android.os.OutcomeReceiver<android.credentials.GetCredentialResponse, android.credentials.GetCredentialException> getPendingCredentialCallback() {
        return delegate.getPendingCredentialCallback();
    }
}
