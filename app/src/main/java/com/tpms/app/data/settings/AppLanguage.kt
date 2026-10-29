package com.tpms.app.data.settings

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** The app's language: the system's, or one of the two the app ships. */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    RUSSIAN("ru");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it != SYSTEM && it.tag == tag?.substringBefore('-') } ?: SYSTEM
    }
}

/**
 * Per-app language. Android 13+: the system's per-app locale ([LocaleManager]) — it re-localizes
 * every context of the process and shows in the system "App language" page. Older versions: kept in
 * plain SharedPreferences (read synchronously in `attachBaseContext`, before DataStore could answer)
 * and applied to the application resources, the activity and the service by hand.
 */
object AppLocale {
    private const val PREFS = "app_locale"
    private const val KEY = "language"

    fun current(context: Context): AppLanguage =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0].language)
        } else {
            stored(context)
        }

    /** Android 13+ recreates the activities itself; older versions need [Activity.recreate]. */
    fun set(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY, language.tag).commit()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == AppLanguage.SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
        } else {
            applyToResources(context.applicationContext)
        }
    }

    /** For `attachBaseContext` of activities and services below Android 13. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val language = stored(base)
        if (language == AppLanguage.SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(language.tag)))
        return base.createConfigurationContext(config)
    }

    /**
     * Below Android 13: localize the application resources (notifications, widgets and receivers
     * build their text from them). Call from `Application.onCreate` / `onConfigurationChanged` —
     * a system configuration change resets them.
     */
    @Suppress("DEPRECATION")
    fun applyToResources(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        val language = stored(context)
        val locales = if (language == AppLanguage.SYSTEM) {
            Resources.getSystem().configuration.locales
        } else {
            LocaleList(Locale.forLanguageTag(language.tag))
        }
        val resources = context.resources
        if (resources.configuration.locales == locales) return
        Locale.setDefault(locales[0])
        val config = Configuration(resources.configuration)
        config.setLocales(locales)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun stored(context: Context): AppLanguage =
        AppLanguage.fromTag(prefs(context).getString(KEY, null))

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
