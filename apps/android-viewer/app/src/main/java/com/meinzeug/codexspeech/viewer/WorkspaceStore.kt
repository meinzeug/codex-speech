package com.meinzeug.codexspeech.viewer

import android.content.Context
import org.json.JSONObject

class WorkspaceStore(context: Context) {
    private val prefs = context.getSharedPreferences("codex_workspace", Context.MODE_PRIVATE)

    fun loadAutoConnect(): Boolean = prefs.getBoolean(KEY_AUTO_CONNECT, false)

    fun saveAutoConnect(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply()
    }

    fun loadAutoStartCodex(): Boolean = prefs.getBoolean(KEY_AUTO_START_CODEX, false)

    fun saveAutoStartCodex(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START_CODEX, enabled).apply()
    }

    fun loadWorkingDir(serverId: String?): String? {
        val raw = prefs.getString(KEY_WORKDIRS, "{}") ?: "{}"
        return try {
            val obj = JSONObject(raw)
            val key = serverIdKey(serverId)
            obj.optString(key).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun saveWorkingDir(serverId: String?, path: String?) {
        val raw = prefs.getString(KEY_WORKDIRS, "{}") ?: "{}"
        val obj = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
        val key = serverIdKey(serverId)
        if (path.isNullOrBlank()) {
            obj.remove(key)
        } else {
            obj.put(key, path)
        }
        prefs.edit().putString(KEY_WORKDIRS, obj.toString()).apply()
    }

    private fun serverIdKey(serverId: String?): String = serverId?.ifBlank { DEFAULT_KEY } ?: DEFAULT_KEY

    companion object {
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_AUTO_START_CODEX = "auto_start_codex"
        private const val KEY_WORKDIRS = "working_dirs"
        private const val DEFAULT_KEY = "_custom"
    }
}
