package com.medicineboxnotes.android

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

object LocaleController {
    const val DEFAULT_LANGUAGE = "en"
    val supported = listOf("en", "zh-CN", "ja", "fr", "de", "es", "ko")
    private const val PREFS = "medicine_box_locale"
    private const val KEY = "language_tag"

    fun currentTag(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY, DEFAULT_LANGUAGE)?.takeIf(supported::contains) ?: DEFAULT_LANGUAGE

    fun wrap(context: Context): Context {
        val locale = Locale.forLanguageTag(currentTag(context))
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }

    @Suppress("DEPRECATION")
    fun applyToApplication(context: Context) {
        val locale = Locale.forLanguageTag(currentTag(context))
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    fun select(activity: Activity, languageTag: String) {
        require(languageTag in supported)
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, languageTag).apply()
        applyToApplication(activity.applicationContext)
        activity.recreate()
    }
}
