package `in`.mrgb.anton.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import `in`.mrgb.anton.data.api.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore(name = "anton")

/**
 * Server connection + notification defaults. The Cloudflare Access service-token
 * pair lives here; DataStore keeps it out of backups' plain files. (If the device
 * is lost, rotate the token in the Zero Trust dashboard.)
 */
class SettingsStore(private val context: Context) {

    private object K {
        val BASE_URL = stringPreferencesKey("base_url")
        val CF_ID = stringPreferencesKey("cf_client_id")
        val CF_SECRET = stringPreferencesKey("cf_client_secret")
        val NOTIFY_NEEDS_YOU = booleanPreferencesKey("notify_needs_you")
        val NOTIFY_FINISHED = booleanPreferencesKey("notify_finished")
        val NOTIFY_WORKING = booleanPreferencesKey("notify_working")
        val DEFAULT_MODEL = stringPreferencesKey("default_model")
        val PLAN_FIRST = booleanPreferencesKey("plan_first")
    }

    val config: Flow<ServerConfig> = context.store.data.map {
        ServerConfig(
            baseUrl = it[K.BASE_URL] ?: "",
            cfClientId = it[K.CF_ID] ?: "",
            cfClientSecret = it[K.CF_SECRET] ?: "",
        )
    }

    val notifyNeedsYou: Flow<Boolean> = context.store.data.map { it[K.NOTIFY_NEEDS_YOU] ?: true }
    val notifyFinished: Flow<Boolean> = context.store.data.map { it[K.NOTIFY_FINISHED] ?: true }
    val notifyWorking: Flow<Boolean> = context.store.data.map { it[K.NOTIFY_WORKING] ?: false }
    val defaultModel: Flow<String?> = context.store.data.map { it[K.DEFAULT_MODEL] }
    val planFirst: Flow<Boolean> = context.store.data.map { it[K.PLAN_FIRST] ?: true }

    suspend fun setServer(url: String, cfId: String, cfSecret: String) {
        context.store.edit {
            it[K.BASE_URL] = url
            it[K.CF_ID] = cfId
            it[K.CF_SECRET] = cfSecret
        }
    }

    suspend fun setNotifyNeedsYou(v: Boolean) = context.store.edit { it[K.NOTIFY_NEEDS_YOU] = v }
    suspend fun setNotifyFinished(v: Boolean) = context.store.edit { it[K.NOTIFY_FINISHED] = v }
    suspend fun setNotifyWorking(v: Boolean) = context.store.edit { it[K.NOTIFY_WORKING] = v }
    suspend fun setDefaultModel(v: String) = context.store.edit { it[K.DEFAULT_MODEL] = v }
    suspend fun setPlanFirst(v: Boolean) = context.store.edit { it[K.PLAN_FIRST] = v }
}
