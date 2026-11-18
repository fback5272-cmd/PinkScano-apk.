package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppLanguage
import com.example.data.model.PageSizeOption
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pinkscan_settings")

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val defaultPageSize: PageSizeOption = PageSizeOption.A4,
    val defaultImageQuality: Int = 85,
    val defaultFilePrefix: String = "PinkScan_",
    val hasCompletedOnboarding: Boolean = false,
    val savedSignaturePath: String? = null
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val DEFAULT_PAGE_SIZE = stringPreferencesKey("default_page_size")
        val DEFAULT_IMAGE_QUALITY = intPreferencesKey("default_image_quality")
        val DEFAULT_FILE_PREFIX = stringPreferencesKey("default_file_prefix")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val SAVED_SIGNATURE_PATH = stringPreferencesKey("saved_signature_path")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.LIGHT,
            language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.ENGLISH,
            defaultPageSize = prefs[Keys.DEFAULT_PAGE_SIZE]?.let { runCatching { PageSizeOption.valueOf(it) }.getOrNull() }
                ?: PageSizeOption.A4,
            defaultImageQuality = prefs[Keys.DEFAULT_IMAGE_QUALITY] ?: 85,
            defaultFilePrefix = prefs[Keys.DEFAULT_FILE_PREFIX] ?: "PinkScan_",
            hasCompletedOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false,
            savedSignaturePath = prefs[Keys.SAVED_SIGNATURE_PATH]
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setDefaultPageSize(size: PageSizeOption) {
        context.dataStore.edit { it[Keys.DEFAULT_PAGE_SIZE] = size.name }
    }

    suspend fun setDefaultImageQuality(quality: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_IMAGE_QUALITY] = quality.coerceIn(40, 100) }
    }

    suspend fun setDefaultFilePrefix(prefix: String) {
        val clean = prefix.trim().ifEmpty { "PinkScan_" }
        context.dataStore.edit { it[Keys.DEFAULT_FILE_PREFIX] = clean }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.HAS_COMPLETED_ONBOARDING] = completed }
    }

    suspend fun setSavedSignaturePath(path: String?) {
        context.dataStore.edit {
            if (path == null) it.remove(Keys.SAVED_SIGNATURE_PATH)
            else it[Keys.SAVED_SIGNATURE_PATH] = path
        }
    }
}
