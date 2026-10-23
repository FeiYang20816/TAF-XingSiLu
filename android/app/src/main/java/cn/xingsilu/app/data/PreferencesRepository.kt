package cn.xingsilu.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map

class PreferencesRepository(context: Context) {
    // The Application owns exactly one repository/store per process.
    private val job = SupervisorJob()
    private val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) {
        context.applicationContext.preferencesDataStoreFile("settings")
    }
    private val themeKey = stringPreferencesKey("appearance")
    private val datesKey = booleanPreferencesKey("show_dates")
    private val sortKey = stringPreferencesKey("sort")
    val values = store.data.map { p -> UserPreferences(
        Appearance.entries.firstOrNull { it.name == p[themeKey] } ?: Appearance.LIGHT,
        p[datesKey] ?: false,
        NoteSort.entries.firstOrNull { it.name == p[sortKey] } ?: NoteSort.UPDATED) }
    suspend fun appearance(value: Appearance) { store.edit { it[themeKey] = value.name } }
    suspend fun showDates(value: Boolean) { store.edit { it[datesKey] = value } }
    suspend fun sort(value: NoteSort) { store.edit { it[sortKey] = value.name } }
    suspend fun close() { job.cancelAndJoin() }
}
