package dev.ringalarmwidget.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.LocaleListCompat

data class LanguageOption(
    val tag: String,
    val label: String?,
)

val LanguageOptions: List<LanguageOption> = listOf(
    LanguageOption("", null),
    LanguageOption("en", "English"),
    LanguageOption("fr", "Français"),
    LanguageOption("nl", "Nederlands"),
    LanguageOption("de", "Deutsch"),
    LanguageOption("es", "Español"),
)

@Composable
@ReadOnlyComposable
fun currentLanguageTag(): String {
    LocalConfiguration.current
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return ""
    return locales.toLanguageTags().substringBefore(',').substringBefore('-')
}

fun applyLanguage(tag: String) {
    AppCompatDelegate.setApplicationLocales(
        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
    )
}
