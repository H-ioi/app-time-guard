package com.apptime.guard.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.apptime.guard.core.model.CastPolicy
import com.apptime.guard.core.model.SecurityLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC = booleanPreferencesKey("biometric_on")
        val RESET_HOUR = intPreferencesKey("reset_hour")
        val RESET_MINUTE = intPreferencesKey("reset_minute")
        val SECURITY_LEVEL = stringPreferencesKey("security_level")
        val CAST_POLICY = stringPreferencesKey("cast_policy")
        val REMIND_LEAD = intPreferencesKey("remind_lead_minutes")
        val SILENT_MODE = booleanPreferencesKey("silent_mode")
        val DAILY_REPORT = booleanPreferencesKey("daily_report")
        val TEMPLATE_ID = stringPreferencesKey("template_id")
        val TIME_OFFSET = longPreferencesKey("trusted_time_offset")
        val TIME_OFFSET_ELAPSED = longPreferencesKey("offset_recorded_elapsed")
        val LAST_SYSTEM_TIME = longPreferencesKey("last_system_time")
        val TAMPER_FLAG = booleanPreferencesKey("time_tamper_flag")
        val WELCOME_DONE = booleanPreferencesKey("welcome_done")
        val CHILD_LOCK_COUNT = intPreferencesKey("child_lock_count")
        val DEVICE_OWNER_GUIDED = booleanPreferencesKey("device_owner_guided")
    }

    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] ?: false }
    suspend fun isOnboarded(): Boolean = onboarded.first()
    suspend fun setOnboarded(v: Boolean) = context.dataStore.edit { it[Keys.ONBOARDED] = v }

    val pinHash: Flow<String?> = context.dataStore.data.map { it[Keys.PIN_HASH] }
    val pinSalt: Flow<String?> = context.dataStore.data.map { it[Keys.PIN_SALT] }
    suspend fun getPinHash(): String? = pinHash.first()
    suspend fun getPinSalt(): String? = pinSalt.first()
    suspend fun setPinHash(hash: String, salt: String) =
        context.dataStore.edit {
            it[Keys.PIN_HASH] = hash
            it[Keys.PIN_SALT] = salt
        }

    val biometricOn: Flow<Boolean> = context.dataStore.data.map { it[Keys.BIOMETRIC] ?: false }
    suspend fun isBiometricOn(): Boolean = biometricOn.first()
    suspend fun setBiometricOn(v: Boolean) = context.dataStore.edit { it[Keys.BIOMETRIC] = v }

    val resetTime: Flow<Pair<Int, Int>> = context.dataStore.data.map {
        it[Keys.RESET_HOUR] ?: 0 to (it[Keys.RESET_MINUTE] ?: 0)
    }
    suspend fun getResetTime(): Pair<Int, Int> = resetTime.first()
    suspend fun setResetTime(hour: Int, minute: Int) =
        context.dataStore.edit {
            it[Keys.RESET_HOUR] = hour
            it[Keys.RESET_MINUTE] = minute
        }

    val securityLevel: Flow<SecurityLevel> = context.dataStore.data.map {
        runCatching { SecurityLevel.valueOf(it[Keys.SECURITY_LEVEL] ?: "") }
            .getOrDefault(SecurityLevel.STANDARD)
    }
    suspend fun getSecurityLevel(): SecurityLevel = securityLevel.first()
    suspend fun setSecurityLevel(v: SecurityLevel) =
        context.dataStore.edit { it[Keys.SECURITY_LEVEL] = v.name }

    val castPolicy: Flow<CastPolicy> = context.dataStore.data.map {
        runCatching { CastPolicy.valueOf(it[Keys.CAST_POLICY] ?: "") }
            .getOrDefault(CastPolicy.COUNT_AS_USE)
    }
    suspend fun getCastPolicy(): CastPolicy = castPolicy.first()
    suspend fun setCastPolicy(v: CastPolicy) = context.dataStore.edit { it[Keys.CAST_POLICY] = v.name }

    val remindLead: Flow<Int> = context.dataStore.data.map { it[Keys.REMIND_LEAD] ?: 5 }
    suspend fun getRemindLead(): Int = remindLead.first()

    val silentMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.SILENT_MODE] ?: false }
    suspend fun getSilentMode(): Boolean = silentMode.first()
    suspend fun setSilentMode(v: Boolean) = context.dataStore.edit { it[Keys.SILENT_MODE] = v }

    val dailyReportOn: Flow<Boolean> = context.dataStore.data.map { it[Keys.DAILY_REPORT] ?: true }

    val templateId: Flow<String?> = context.dataStore.data.map { it[Keys.TEMPLATE_ID] }

    suspend fun getTimeOffset(): Long = context.dataStore.data.first()[Keys.TIME_OFFSET] ?: 0L
    suspend fun setTimeOffset(offset: Long, elapsed: Long) =
        context.dataStore.edit {
            it[Keys.TIME_OFFSET] = offset
            it[Keys.TIME_OFFSET_ELAPSED] = elapsed
        }

    suspend fun getLastSystemTime(): Long = context.dataStore.data.first()[Keys.LAST_SYSTEM_TIME] ?: 0L
    suspend fun setLastSystemTime(ts: Long) = context.dataStore.edit { it[Keys.LAST_SYSTEM_TIME] = ts }

    val tamperFlag: Flow<Boolean> = context.dataStore.data.map { it[Keys.TAMPER_FLAG] ?: false }
    suspend fun isTampered(): Boolean = tamperFlag.first()
    suspend fun setTampered(v: Boolean) = context.dataStore.edit { it[Keys.TAMPER_FLAG] = v }

    suspend fun setTemplateId(id: String) = context.dataStore.edit { it[Keys.TEMPLATE_ID] = id }

    suspend fun setWelcomeDone() = context.dataStore.edit { it[Keys.WELCOME_DONE] = true }
    suspend fun isWelcomeDone(): Boolean =
        context.dataStore.data.first()[Keys.WELCOME_DONE] ?: false

    val childLockCount: Flow<Int> = context.dataStore.data.map { it[Keys.CHILD_LOCK_COUNT] ?: 0 }
    suspend fun getChildLockCount(): Int = childLockCount.first()
    suspend fun incChildLockCount() =
        context.dataStore.edit { it[Keys.CHILD_LOCK_COUNT] = (it[Keys.CHILD_LOCK_COUNT] ?: 0) + 1 }

    suspend fun setDeviceOwnerGuided() = context.dataStore.edit { it[Keys.DEVICE_OWNER_GUIDED] = true }
    suspend fun isDeviceOwnerGuided(): Boolean =
        context.dataStore.data.first()[Keys.DEVICE_OWNER_GUIDED] ?: false
}
