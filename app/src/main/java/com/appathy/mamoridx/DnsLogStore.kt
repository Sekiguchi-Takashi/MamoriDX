package com.appathy.mamoridx

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * DNS問い合わせログとドメイン別ポリシーの保管庫。
 * VpnService と Activity の双方から参照する。永続化は SharedPreferences。
 */
object DnsLogStore {

    private const val PREF = "mamoridx_dns"
    private const val KEY_LOG = "log"
    private const val KEY_POLICY = "policy"
    private const val MAX_ENTRIES = 500

    // ポリシー種別
    const val POLICY_ALLOW = 0   // 許可
    const val POLICY_RECORD = 1  // 記録のみ（既定）
    const val POLICY_BLOCK = 2   // ブロック（A案では実効化せず記録のみ・将来Phase2.5で実装）

    data class Entry(val domain: String, val time: Long, var count: Int)

    private val entries = LinkedHashMap<String, Entry>()   // domain -> entry
    private val policies = HashMap<String, Int>()          // domain -> policy
    private var loaded = false

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        try {
            val arr = JSONArray(p.getString(KEY_LOG, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val d = o.getString("d")
                entries[d] = Entry(d, o.getLong("t"), o.getInt("c"))
            }
        } catch (e: Exception) { }
        try {
            val o = JSONObject(p.getString(KEY_POLICY, "{}"))
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                policies[k] = o.getInt(k)
            }
        } catch (e: Exception) { }
        loaded = true
    }

    @Synchronized
    fun record(ctx: Context, domain: String) {
        if (domain.isBlank()) return
        val now = System.currentTimeMillis()
        val e = entries[domain]
        if (e != null) {
            e.count += 1
        } else {
            if (entries.size >= MAX_ENTRIES) {
                val oldest = entries.keys.firstOrNull()
                if (oldest != null) entries.remove(oldest)
            }
            entries[domain] = Entry(domain, now, 1)
        }
        persistLog(ctx)
    }

    @Synchronized
    fun snapshot(): List<Entry> = entries.values.sortedByDescending { it.count }.map {
        Entry(it.domain, it.time, it.count)
    }

    @Synchronized
    fun getPolicy(domain: String): Int = policies[domain] ?: POLICY_RECORD

    @Synchronized
    fun setPolicy(ctx: Context, domain: String, policy: Int) {
        policies[domain] = policy
        val o = JSONObject()
        for ((k, v) in policies) o.put(k, v)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_POLICY, o.toString()).apply()
    }

    @Synchronized
    fun clear(ctx: Context) {
        entries.clear()
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_LOG, "[]").apply()
    }

    private fun persistLog(ctx: Context) {
        val arr = JSONArray()
        for (e in entries.values) {
            arr.put(JSONObject().apply {
                put("d", e.domain)
                put("t", e.time)
                put("c", e.count)
            })
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_LOG, arr.toString()).apply()
    }
}
