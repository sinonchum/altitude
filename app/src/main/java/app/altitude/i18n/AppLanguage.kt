package app.altitude.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import app.altitude.R
import java.util.Locale

enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    CHINESE("zh-CN"),
    ENGLISH("en"),
    FRENCH("fr"),
    ;

    @StringRes
    fun labelRes(): Int = when (this) {
        SYSTEM -> R.string.language_system
        CHINESE -> R.string.language_zh
        ENGLISH -> R.string.language_en
        FRENCH -> R.string.language_fr
    }

    companion object {
        fun current(): AppLanguage = fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

        fun fromTags(tags: String): AppLanguage {
            if (tags.isBlank()) return SYSTEM
            return when (tags.substringBefore(',').substringBefore('-').lowercase()) {
                "zh" -> CHINESE
                "en" -> ENGLISH
                "fr" -> FRENCH
                else -> SYSTEM
            }
        }
    }
}

fun AppLanguage.toLocale(): Locale = when (this) {
    AppLanguage.ENGLISH -> Locale.forLanguageTag("en")
    AppLanguage.FRENCH -> Locale.forLanguageTag("fr")
    AppLanguage.CHINESE -> Locale.forLanguageTag("zh-CN")
    AppLanguage.SYSTEM -> systemLocale()
}

private fun systemLocale(): Locale {
    val configuration = Resources.getSystem().configuration
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        configuration.locales[0]
    } else {
        @Suppress("DEPRECATION")
        configuration.locale
    }
}

fun applyAppLanguage(context: Context, language: AppLanguage) {
    val locales = if (language.tag == null) {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(language.tag)
    }
    AppCompatDelegate.setApplicationLocales(locales)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java)?.applicationLocales =
            LocaleList.forLanguageTags(locales.toLanguageTags())
    }
}
