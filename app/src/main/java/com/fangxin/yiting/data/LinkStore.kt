package com.fangxin.yiting.data

import android.content.Context
import com.fangxin.yiting.domain.AlertEvent
import com.fangxin.yiting.domain.CheckSource
import com.fangxin.yiting.domain.RiskDecision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/** 配对码与家人端收到的警报，只存在本机。 */
class LinkStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("link", Context.MODE_PRIVATE)

    private val _alerts = MutableStateFlow(loadAlerts())
    val alerts: StateFlow<List<AlertEvent>> = _alerts.asStateFlow()

    val elderRoom: String get() = prefs.getString(KEY_ROOM, "").orEmpty()

    val guardianRoom: String get() = prefs.getString(KEY_GUARDIAN_ROOM, "").orEmpty()

    val guardianPaired: Boolean get() = isValidRoom(guardianRoom)

    @Synchronized
    fun ensureElderRoom(): String {
        val current = elderRoom
        if (isValidRoom(current)) return current
        val fresh = (100_000_000 + SecureRandom().nextInt(900_000_000)).toString()
        prefs.edit().putString(KEY_ROOM, fresh).apply()
        return fresh
    }

    fun saveGuardianPairing(room: String) {
        prefs.edit().putString(KEY_GUARDIAN_ROOM, room).apply()
    }

    fun clearGuardianPairing() {
        prefs.edit().remove(KEY_GUARDIAN_ROOM).apply()
    }

    @Synchronized
    fun addAlert(alert: AlertEvent): Boolean {
        val existing = _alerts.value
        if (existing.any { it.id == alert.id }) return false
        val updated = (listOf(alert) + existing).take(MAX_ALERTS)
        _alerts.value = updated
        prefs.edit().putString(KEY_ALERTS, encodeAlerts(updated)).apply()
        return true
    }

    @Synchronized
    fun clearAlerts() {
        _alerts.value = emptyList()
        prefs.edit().remove(KEY_ALERTS).apply()
    }

    private fun loadAlerts(): List<AlertEvent> {
        val raw = prefs.getString(KEY_ALERTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        AlertEvent(
                            id = item.optString("id"),
                            risk = RiskDecision.values().firstOrNull { it.name == item.optString("risk") }
                                ?: RiskDecision.UNKNOWN,
                            label = item.optString("label"),
                            plain = item.optString("plain"),
                            maskedText = item.optString("maskedText"),
                            actions = arrayToList(item.optJSONArray("actions")),
                            source = CheckSource.values().firstOrNull { it.name == item.optString("source") }
                                ?: CheckSource.VOICE,
                            createdAt = item.optLong("createdAt"),
                        ),
                    )
                }
            }
        } catch (error: Exception) {
            emptyList()
        }
    }

    private fun encodeAlerts(items: List<AlertEvent>): String {
        val array = JSONArray()
        for (item in items) {
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("risk", item.risk.name)
                    .put("label", item.label)
                    .put("plain", item.plain)
                    .put("maskedText", item.maskedText)
                    .put("actions", JSONArray(item.actions))
                    .put("source", item.source.name)
                    .put("createdAt", item.createdAt),
            )
        }
        return array.toString()
    }

    private fun arrayToList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) add(array.optString(index))
        }
    }

    private fun isValidRoom(room: String): Boolean =
        (room.length == 6 || room.length == 9) && room.all { it.isDigit() }

    companion object {
        private const val KEY_ROOM = "elder_room"
        private const val KEY_GUARDIAN_ROOM = "guardian_room"
        private const val KEY_ALERTS = "guardian_alerts"
        private const val MAX_ALERTS = 20

        @Volatile
        private var instance: LinkStore? = null

        fun get(context: Context): LinkStore =
            instance ?: synchronized(this) {
                instance ?: LinkStore(context).also { instance = it }
            }
    }
}
