package com.psecurity.psblock

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.psecurity.psblock.PSBlockPrefs.appLanguage
import java.util.Locale

object AppLanguageManager {
    const val LANG_EN = "en"
    const val LANG_PT_BR = "pt-BR"

    private val lock = Any()
    private var cachedTextLanguage: String? = null
    private var cachedTextContext: Context? = null
    private var lastDefaultLocaleTag: String? = null

    fun normalize(language: String?): String {
        return when (language) {
            LANG_PT_BR, "pt", "pt_BR" -> LANG_PT_BR
            else -> LANG_EN
        }
    }

    fun currentLanguage(context: Context): String {
        return normalize(context.appLanguage)
    }

    fun setLanguage(context: Context, language: String): Boolean {
        val normalized = normalize(language)
        val previous = currentLanguage(context)
        context.appLanguage = normalized
        if (previous != normalized) {
            clearRuntimeCache()
            return true
        }
        return false
    }

    fun wrap(base: Context): Context {
        val language = currentLanguage(base)
        val locale = localeFor(language)
        applyDefaultLocaleIfNeeded(language, locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))

        return base.createConfigurationContext(config)
    }

    fun text(context: Context, resId: Int, vararg args: Any): String {
        val language = currentLanguage(context)
        val wrapped = cachedTextContext(context.applicationContext, language)

        return if (args.isEmpty()) {
            wrapped.getString(resId)
        } else {
            wrapped.getString(resId, *args)
        }
    }

    private fun cachedTextContext(appContext: Context, language: String): Context {
        synchronized(lock) {
            val existing = cachedTextContext
            if (existing != null && cachedTextLanguage == language) {
                return existing
            }

            val locale = localeFor(language)
            applyDefaultLocaleIfNeeded(language, locale)

            val config = Configuration(appContext.resources.configuration)
            config.setLocale(locale)
            config.setLocales(LocaleList(locale))

            return appContext.createConfigurationContext(config).also {
                cachedTextLanguage = language
                cachedTextContext = it
            }
        }
    }

    private fun localeFor(language: String): Locale {
        return if (language == LANG_PT_BR) Locale("pt", "BR") else Locale.ENGLISH
    }

    private fun applyDefaultLocaleIfNeeded(language: String, locale: Locale) {
        synchronized(lock) {
            if (lastDefaultLocaleTag != language || Locale.getDefault() != locale) {
                Locale.setDefault(locale)
                lastDefaultLocaleTag = language
            }
        }
    }

    private fun clearRuntimeCache() {
        synchronized(lock) {
            cachedTextLanguage = null
            cachedTextContext = null
            lastDefaultLocaleTag = null
        }
    }
}
