package com.example

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    companion object {
        val MODE = booleanPreferencesKey("mode_continuous")
        val INTERVAL = intPreferencesKey("interval")
        val RETENTION = intPreferencesKey("retention")
        val AUTO_RESTART = booleanPreferencesKey("auto_restart")
        val MOTION_THRESHOLD = intPreferencesKey("motion_threshold")
        val IS_ENHANCED_MODE = booleanPreferencesKey("is_enhanced_mode")
        val IS_HDR_MODE = booleanPreferencesKey("is_hdr_mode")
        val ASPECT_RATIO = intPreferencesKey("aspect_ratio")
        val IS_FAR_ONLY_MODE = booleanPreferencesKey("is_far_only_mode")
        val FAR_FOCUS_LOCK = booleanPreferencesKey("far_focus_lock")
        val NEAR_EXCLUSION_THRESHOLD = intPreferencesKey("near_exclusion_threshold")
        val FAR_SENSITIVITY = intPreferencesKey("far_sensitivity")
    }

    val isContinuousMode: Flow<Boolean> = context.dataStore.data.map { it[MODE] ?: true }
    val interval: Flow<Int> = context.dataStore.data.map { it[INTERVAL] ?: 5 }
    val retentionDays: Flow<Int> = context.dataStore.data.map { it[RETENTION] ?: 7 }
    val autoRestart: Flow<Boolean> = context.dataStore.data.map { it[AUTO_RESTART] ?: false }
    val motionThreshold: Flow<Int> = context.dataStore.data.map { it[MOTION_THRESHOLD] ?: 20 }
    val isEnhancedMode: Flow<Boolean> = context.dataStore.data.map { it[IS_ENHANCED_MODE] ?: true }
    val isHdrMode: Flow<Boolean> = context.dataStore.data.map { it[IS_HDR_MODE] ?: true }
    val aspectRatio: Flow<Int> = context.dataStore.data.map { it[ASPECT_RATIO] ?: 0 } // 0 for 4:3, 1 for 16:9
    val isFarOnlyMode: Flow<Boolean> = context.dataStore.data.map { it[IS_FAR_ONLY_MODE] ?: false }
    val farFocusLock: Flow<Boolean> = context.dataStore.data.map { it[FAR_FOCUS_LOCK] ?: true }
    val nearExclusionThreshold: Flow<Int> = context.dataStore.data.map { it[NEAR_EXCLUSION_THRESHOLD] ?: 25 }
    val farSensitivity: Flow<Int> = context.dataStore.data.map { it[FAR_SENSITIVITY] ?: 15 }

    suspend fun setMode(isContinuous: Boolean) {
        context.dataStore.edit { it[MODE] = isContinuous }
    }
    
    suspend fun setInterval(seconds: Int) {
        context.dataStore.edit { it[INTERVAL] = seconds }
    }
    
    suspend fun setRetention(days: Int) {
        context.dataStore.edit { it[RETENTION] = days }
    }
    
    suspend fun setAutoRestart(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_RESTART] = enabled }
    }
    
    suspend fun setMotionThreshold(threshold: Int) {
        context.dataStore.edit { it[MOTION_THRESHOLD] = threshold }
    }
    
    suspend fun setEnhancedMode(isEnhanced: Boolean) {
        context.dataStore.edit { it[IS_ENHANCED_MODE] = isEnhanced }
    }
    
    suspend fun setHdrMode(isHdr: Boolean) {
        context.dataStore.edit { it[IS_HDR_MODE] = isHdr }
    }
    
    suspend fun setAspectRatio(ratio: Int) {
        context.dataStore.edit { it[ASPECT_RATIO] = ratio }
    }

    suspend fun setFarOnlyMode(enabled: Boolean) {
        context.dataStore.edit { it[IS_FAR_ONLY_MODE] = enabled }
    }

    suspend fun setFarFocusLock(enabled: Boolean) {
        context.dataStore.edit { it[FAR_FOCUS_LOCK] = enabled }
    }

    suspend fun setNearExclusionThreshold(threshold: Int) {
        context.dataStore.edit { it[NEAR_EXCLUSION_THRESHOLD] = threshold }
    }

    suspend fun setFarSensitivity(sensitivity: Int) {
        context.dataStore.edit { it[FAR_SENSITIVITY] = sensitivity }
    }
}
