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
    private val usageKey = stringPreferencesKey("usage_stats")
    private val chatKey = stringPreferencesKey("chat_history")

    val keys: Flow<List<ApiKeyEntry>> = context.dataStore.data.map { prefs ->
        prefs[keysKey]?.let { raw ->
            runCatching { json.decodeFromString<List<ApiKeyEntry>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    val activeKeyId: Flow<String?> = context.dataStore.data.map { it[activeKey] }

    /** The last chat session, restored on relaunch. */
    val chat: Flow<List<ChatMessage>> = context.dataStore.data.map { prefs ->
        prefs[chatKey]?.let { raw ->
            runCatching { json.decodeFromString<List<ChatMessage>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun saveChat(list: List<ChatMessage>) {
        // ponytail: drop image blobs before persisting — keeps the prefs entry
        // small; reopened chats show text only, which is fine for history.
        val slim = list.map { it.copy(imageBase64 = null, imageMime = null) }
        context.dataStore.edit { it[chatKey] = json.encodeToString(slim) }
    }

    val usage: Flow<List<UsageStat>> = context.dataStore.data.map { prefs ->
        prefs[usageKey]?.let { raw ->
            runCatching { json.decodeFromString<List<UsageStat>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun saveKeys(list: List<ApiKeyEntry>) {
        context.dataStore.edit { it[keysKey] = json.encodeToString(list) }
    }

    suspend fun setActive(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(activeKey) else prefs[activeKey] = id
        }
    }

    /** Folds one completed exchange into the per-model usage tally. */
    suspend fun recordUsage(model: String, provider: Provider, charsReceived: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[usageKey]?.let {
                runCatching { json.decodeFromString<List<UsageStat>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val list = current.toMutableList()
            val idx = list.indexOfFirst { it.model == model && it.provider == provider }
            if (idx >= 0) {
                val s = list[idx]
                list[idx] = s.copy(messages = s.messages + 1, charsReceived = s.charsReceived + charsReceived)
            } else {
                list.add(UsageStat(model, provider, messages = 1, charsReceived = charsReceived))
            }
            prefs[usageKey] = json.encodeToString(list)
        }
    }

    suspend fun clearUsage() {
        context.dataStore.edit { it.remove(usageKey) }
    }
}
