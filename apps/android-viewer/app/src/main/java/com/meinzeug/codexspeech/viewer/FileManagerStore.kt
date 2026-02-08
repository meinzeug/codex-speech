package com.meinzeug.codexspeech.viewer

import android.content.Context
import org.json.JSONArray

class FileManagerStore(context: Context) {
    private val prefs = context.getSharedPreferences("codex_file_manager", Context.MODE_PRIVATE)

    fun loadFavorites(): List<String> = loadList("favorites")

    fun saveFavorites(list: List<String>) {
        saveList("favorites", list)
    }

    fun loadRecents(): List<String> = loadList("recents")

    fun addRecent(path: String) {
        val current = loadRecents().toMutableList()
        current.remove(path)
        current.add(0, path)
        saveList("recents", current.take(20))
    }

    private fun loadList(key: String): List<String> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                arr.optString(idx)?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveList(key: String, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
