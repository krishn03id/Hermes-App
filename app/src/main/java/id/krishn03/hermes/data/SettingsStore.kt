package id.krishn03.hermes.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hermes_settings")

/**
 * Persists the list of API keys and which one is active, as JSON inside a
 * single DataStore preferences file. Nothing leaves the device.
 */
class SettingsStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val keysKey = stringPreferencesKey("api_keys")
    private val activeKey = stringPreferencesKey("active_key_id")

    val keys: Flow<List<ApiKeyEntry>> = context.dataStore.data.map { prefs ->
        prefs[keysKey]?.let { raw ->
            runCatching { json.decodeFromString<List<ApiKeyEntry>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    val activeKeyId: Flow<String?> = context.dataStore.data.map { it[activeKey] }

    suspend fun saveKeys(list: List<ApiKeyEntry>) {
        context.dataStore.edit { it[keysKey] = json.encodeToString(list) }
    }

    suspend fun setActive(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(activeKey) else prefs[activeKey] = id
        }
    }
}
