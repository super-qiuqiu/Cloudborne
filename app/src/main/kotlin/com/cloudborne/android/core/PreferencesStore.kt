package com.cloudborne.android.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")

class PreferencesStore(private val context: Context) {
    private val selectedNodeKey = stringPreferencesKey("selected_node_id")

    suspend fun selectedNodeId(): String? = context.userPreferencesDataStore.data.first()[selectedNodeKey]

    suspend fun setSelectedNodeId(id: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[selectedNodeKey] = id
        }
    }
}
