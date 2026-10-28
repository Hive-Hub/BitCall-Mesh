package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bitcall_security_prefs")

class SecurityPreferences(private val context: Context) {
    companion object {
        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
        val KEY_FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val KEY_LOCKOUT_UNTIL = longPreferencesKey("lockout_until")
        val KEY_RELAY_ENABLED = booleanPreferencesKey("relay_enabled")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_APP_LOCK_ENABLED] ?: false }
    val pinHash: Flow<String?> = context.dataStore.data.map { it[KEY_PIN_HASH] }
    val pinSalt: Flow<String?> = context.dataStore.data.map { it[KEY_PIN_SALT] }
    val failedAttempts: Flow<Int> = context.dataStore.data.map { it[KEY_FAILED_ATTEMPTS] ?: 0 }
    val lockoutUntil: Flow<Long> = context.dataStore.data.map { it[KEY_LOCKOUT_UNTIL] ?: 0L }
    val relayEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_RELAY_ENABLED] ?: true }
    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDING_COMPLETED] ?: false }

    suspend fun setAppLock(pinHash: String, pinSalt: String) {
        context.dataStore.edit {
            it[KEY_APP_LOCK_ENABLED] = true
            it[KEY_PIN_HASH] = pinHash
            it[KEY_PIN_SALT] = pinSalt
            it[KEY_FAILED_ATTEMPTS] = 0
            it[KEY_LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun disableAppLock() {
        context.dataStore.edit {
            it[KEY_APP_LOCK_ENABLED] = false
            it.remove(KEY_PIN_HASH)
            it.remove(KEY_PIN_SALT)
            it[KEY_FAILED_ATTEMPTS] = 0
            it[KEY_LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun recordFailedAttempt(lockoutDurationMs: Long = 30_000L) {
        context.dataStore.edit {
            val current = (it[KEY_FAILED_ATTEMPTS] ?: 0) + 1
            it[KEY_FAILED_ATTEMPTS] = current
            if (current >= 5) {
                it[KEY_LOCKOUT_UNTIL] = System.currentTimeMillis() + lockoutDurationMs
            }
        }
    }

    suspend fun resetFailedAttempts() {
        context.dataStore.edit {
            it[KEY_FAILED_ATTEMPTS] = 0
            it[KEY_LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun setRelayEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_RELAY_ENABLED] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }
}
