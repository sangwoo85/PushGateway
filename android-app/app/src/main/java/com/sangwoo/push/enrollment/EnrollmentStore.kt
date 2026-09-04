package com.sangwoo.push.enrollment

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.enrollmentDataStore by preferencesDataStore(name = "depl_enrollment")

@Serializable
data class EnrollmentRecord(
    val current: EnrollmentTopics? = null,
    val cleanup: Set<String> = emptySet()
)

interface EnrollmentRecordStore {
    suspend fun load(): EnrollmentRecord
    suspend fun save(record: EnrollmentRecord)
    suspend fun clear()
}

class EnrollmentStore(private val context: Context) : EnrollmentRecordStore {
    private val key = stringPreferencesKey("registration")
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): EnrollmentRecord = context.enrollmentDataStore.data.first()[key]
        ?.let { runCatching { json.decodeFromString<EnrollmentRecord>(it) }.getOrNull() }
        ?: EnrollmentRecord()

    override suspend fun save(record: EnrollmentRecord) {
        context.enrollmentDataStore.edit { it[key] = json.encodeToString(record) }
    }

    override suspend fun clear() {
        context.enrollmentDataStore.edit { it.remove(key) }
    }
}
