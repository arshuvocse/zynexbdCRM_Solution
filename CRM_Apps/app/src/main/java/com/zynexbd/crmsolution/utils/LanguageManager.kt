package com.zynexbd.crmsolution.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.zynexbd.crmsolution.R
import java.util.Locale

/**
 * Manages language selection ("en" for English, "bn" for Bangla)
 * and dynamically applies fonts:
 * - English ("en") -> Google Roboto font
 * - Bangla ("bn")  -> SolaimanLipi font
 */
object LanguageManager {

    private const val PREFS_NAME = "app_language_prefs"
    private const val KEY_LANG = "selected_language"

    const val LANG_EN = "en"
    const val LANG_BN = "bn"

    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANG, LANG_BN) ?: LANG_BN
    }

    fun setLanguage(context: Context, lang: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, lang).apply()
    }

    fun isEnglish(context: Context): Boolean = getLanguage(context) == LANG_EN
    fun isBangla(context: Context): Boolean = getLanguage(context) == LANG_BN

    fun applyLocale(context: Context): Context {
        val lang = getLanguage(context)
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val res = context.resources
        val config = Configuration(res.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)
        return context.createConfigurationContext(config)
    }

    fun getTypeface(context: Context): Typeface? {
        val lang = getLanguage(context)
        val fontResId = if (lang == LANG_EN) R.font.roboto else R.font.solaimanlipi
        return try {
            ResourcesCompat.getFont(context, fontResId)
        } catch (e: Exception) {
            null
        }
    }

    fun applyFontRecursively(view: View, typeface: Typeface?) {
        if (typeface == null) return
        if (view is TextView) {
            view.typeface = typeface
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyFontRecursively(view.getChildAt(i), typeface)
            }
        }
    }
}
