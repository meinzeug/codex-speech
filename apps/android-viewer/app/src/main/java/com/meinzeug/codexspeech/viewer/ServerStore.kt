package com.meinzeug.codexspeech.viewer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID


data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: String,
    val workingDir: String,
)

class ServerStore(context: Context) {
    private val prefs = context.getSharedPreferences("codex_servers", Context.MODE_PRIVATE)

    fun load(): List<ServerProfile> {
        val raw = prefs.getString(KEY_SERVERS, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        ServerProfile(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            host = obj.optString("host"),
                            port = obj.optString("port"),
                            workingDir = obj.optString("workingDir"),
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(servers: List<ServerProfile>) {
        val array = JSONArray()
        servers.forEach { server ->
            val obj = JSONObject()
            obj.put("id", server.id)
            obj.put("name", server.name)
            obj.put("host", server.host)
            obj.put("port", server.port)
            obj.put("workingDir", server.workingDir)
            array.put(obj)
        }
        prefs.edit().putString(KEY_SERVERS, array.toString()).apply()
    }

    fun loadSelectedServerId(): String? {
        return prefs.getString(KEY_SELECTED, null)
    }

    fun saveSelectedServerId(id: String?) {
        prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    companion object {
        private const val KEY_SERVERS = "servers"
        private const val KEY_SELECTED = "selectedServerId"
    }
}
