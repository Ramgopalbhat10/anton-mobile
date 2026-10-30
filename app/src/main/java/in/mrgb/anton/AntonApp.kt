package `in`.mrgb.anton

import android.app.Application
import `in`.mrgb.anton.data.AntonRepository
import `in`.mrgb.anton.data.SettingsStore
import `in`.mrgb.anton.data.api.AntonApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AntonApp : Application() {

    lateinit var store: SettingsStore
        private set
    lateinit var repository: AntonRepository
        private set

    override fun onCreate() {
        super.onCreate()
        store = SettingsStore(this)
        // The API reads config lazily, so Settings edits apply to the next request.
        val api = AntonApi { runBlocking { store.config.first() } }
        repository = AntonRepository(api)
    }
}
