package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mlkit.nl.translate.TranslateLanguage
import com.takeruf.nagi.R
import com.takeruf.nagi.browser.translation.*
import com.takeruf.nagi.ui.components.NagiIcons
import com.takeruf.nagi.ui.components.NagiOverflowMenu
import com.takeruf.nagi.ui.components.NagiOverflowMenuItem
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes
import java.util.Locale

data class PageTranslationRequest(val tabId: String, val engine: com.takeruf.nagi.browser.engine.BrowserEngine, val url: String)

@Composable
fun PageTranslationNotice(state: PageTranslationState, onDismiss: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    targetLanguage: String? = null, onTargetLanguageChange: (String) -> Unit = {},
    alwaysTranslateLanguages: Map<String, String> = emptyMap(),
    onAlwaysTranslationChange: (String, String?) -> Unit = { _, _ -> },
    onTranslate: (String?, String, Boolean, Boolean) -> Unit) {
    val strings = rememberNagiStrings()
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val languages = remember(locale) {
        TranslateLanguage.getAllLanguages().sortedWith(compareBy<String> {
            when (it) { locale.language -> 0; "ja" -> 1; "en" -> 2; "zh" -> 3; "ko" -> 4; else -> 5 }
        }.thenBy { Locale.forLanguageTag(it).getDisplayLanguage(locale) })
    }
    var source by rememberSaveable { mutableStateOf<String?>(null) }
    var target by rememberSaveable(targetLanguage, locale.language) {
        mutableStateOf(targetLanguage?.takeIf { it in languages } ?: locale.language.takeIf { it in languages } ?: "en")
    }
    var wifiOnly by rememberSaveable { mutableStateOf(true) }
    var about by rememberSaveable { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var alwaysOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val ruleSource = source ?: state.source
    val always = alwaysOverride ?: (ruleSource != null && ruleSource in alwaysTranslateLanguages)
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    BrowserNoticeCard(strings(R.string.ui_translate_page), strings(R.string.ui_cancel), onDismiss,
        modifier.heightIn(max = (configuration.screenHeightDp - 104).coerceAtLeast(160).dp).testTag("translation-notice"),
        header = {
            Row(Modifier.padding(start = 16.dp, end = 8.dp)) {
                Text(strings(if (about) R.string.ui_translation_about else R.string.ui_translation_from),
                    Modifier.weight(1f).padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
                if (about) IconButton(onClick = { about = false }, modifier = Modifier.testTag("translation-about-back")) {
                    Icon(NagiIcons.ArrowBack, strings(R.string.ui_back), Modifier.size(18.dp))
                } else Box {
                    IconButton(onClick = { more = true }, modifier = Modifier.testTag("translation-more")) {
                        Icon(NagiIcons.Ellipsis, strings(R.string.ui_translation_more), Modifier.size(18.dp))
                    }
                    NagiOverflowMenu(more, { more = false }) {
                        NagiOverflowMenuItem(text = { Text(strings(R.string.ui_translation_always)) },
                            leadingIcon = { Checkbox(always, onCheckedChange = null, modifier = Modifier.size(20.dp)) },
                            modifier = Modifier.testTag("translation-always"),
                            onClick = {
                                alwaysOverride = !always
                                ruleSource?.let { onAlwaysTranslationChange(it, if (!always) target else null) }
                                more = false
                            })
                        NagiOverflowMenuItem(text = { Text(strings(R.string.ui_translation_wifi)) },
                            leadingIcon = { Checkbox(wifiOnly, onCheckedChange = null, modifier = Modifier.size(20.dp)) },
                            modifier = Modifier.testTag("translation-wifi"),
                            onClick = { wifiOnly = !wifiOnly; more = false })
                        NagiOverflowMenuItem(text = { Text(strings(R.string.ui_translation_about)) },
                            leadingIcon = { Icon(NagiIcons.BookOpen, null) }, modifier = Modifier.testTag("translation-about"),
                            onClick = { more = false; about = true })
                    }
                }
            }
        }) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (about) {
                Text(strings(R.string.ui_translation_on_device), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(strings(R.string.ui_translation_privacy), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(strings(R.string.google_translation_disclaimer), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TranslationLanguagePicker(null, source, languages, locale,
                    strings(R.string.ui_translation_auto), "translation-source") { source = it }
                TranslationLanguagePicker(strings(R.string.ui_translation_to), target, languages, locale,
                    null, "translation-target") { language ->
                    if (language != null) {
                        target = language
                        onTargetLanguageChange(language)
                        if (always) ruleSource?.let { onAlwaysTranslationChange(it, language) }
                    }
                }
            }
        }
        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss, shape = NagiShapes.Rounded,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(strings(R.string.ui_cancel))
            }
            val translateLabel = strings(R.string.ui_translate_with_google)
            Button(onClick = { if (about) about = false else onTranslate(source, target, wifiOnly, always) },
                enabled = about || (enabled && source != target),
                shape = NagiShapes.Rounded, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("translation-start"),
                contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(strings(if (about) R.string.ui_back else R.string.ui_translate_with_google),
                    Modifier.semantics { if (!about) contentDescription = translateLabel })
            }
        }
    }
}

@Composable
private fun TranslationLanguagePicker(label: String?, value: String?, languages: List<String>, locale: Locale,
    auto: String?, tag: String, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag(tag), shape = NagiShapes.Rounded) {
                Text(value?.let { Locale.forLanguageTag(it).getDisplayLanguage(locale) } ?: auto.orEmpty(), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(NagiIcons.ChevronDown, null, Modifier.size(18.dp))
            }
            DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                if (auto != null) DropdownMenuItem(text = { Text(auto) }, modifier = Modifier.testTag("$tag-auto"),
                    onClick = { onSelect(null); expanded = false })
                languages.forEach { code ->
                    DropdownMenuItem(text = { Text(Locale.forLanguageTag(code).getDisplayLanguage(locale)) },
                        modifier = Modifier.testTag("$tag-$code"), onClick = { onSelect(code); expanded = false })
                }
            }
        }
    }
}

@Composable
fun PageTranslationBar(state: PageTranslationState, onRestore: () -> Unit) {
    if (!state.active) return
    val strings = rememberNagiStrings()
    val locale = LocalConfiguration.current.locales[0]
    val status = when (state.stage) {
        TranslationStage.DETECTING -> strings(R.string.ui_translation_detecting)
        TranslationStage.DOWNLOADING -> strings(R.string.ui_translation_downloading)
        TranslationStage.TRANSLATING -> strings(R.string.ui_translation_working)
        TranslationStage.TRANSLATED -> strings(R.string.ui_translation_done,
            Locale.forLanguageTag(state.target.orEmpty()).getDisplayLanguage(locale))
        else -> strings(R.string.ui_translation_failed)
    }
    val message = state.error?.let { error ->
        strings(when (error) {
            TranslationError.NO_TEXT -> R.string.ui_translation_no_text
            TranslationError.LANGUAGE -> R.string.ui_translation_language_error
            TranslationError.SAME_LANGUAGE -> R.string.ui_translation_same_language
            TranslationError.MODEL -> R.string.ui_translation_model_error
            TranslationError.LIMIT -> R.string.ui_translation_limit
            TranslationError.TRANSLATION -> R.string.ui_translation_failed
        })
    } ?: status
    BrowserStatusBar(message,
        strings(if (state.busy && state.translatedNodes == 0) R.string.ui_cancel else R.string.ui_translation_original),
        onRestore, modifier = Modifier.testTag("translation-bar"),
        messageModifier = Modifier.testTag("translation-status"),
        actionModifier = Modifier.testTag("translation-restore"), busy = state.busy,
        attribution = {
            Image(painterResource(if (MaterialTheme.colorScheme.surfaceContainer.luminance() < 0.5f)
                R.drawable.google_translate_attribution_white else R.drawable.google_translate_attribution_greyscale),
                strings(R.string.ui_google_translation_attribution),
                Modifier.size(width = 132.dp, height = 12.dp).testTag("translation-attribution"))
        })
}
