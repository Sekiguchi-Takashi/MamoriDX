package com.appathy.mamoridx

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 進行状況の保存・復元 */
object GameState {

    private const val PREF = "nangoku_save"
    private const val KEY = "state"

    var seed: Long = 0L
    var treasureAreaId: String = ""
    var clues: MutableList<GameData.Clue> = mutableListOf()      // 全7つ（内部用）
    var fragmentAreaIds: MutableList<String> = mutableListOf()
    var foundFragments: MutableSet<Int> = mutableSetOf()          // 見つけた欠片の番号
    var foundItems: MutableSet<String> = mutableSetOf()
    var visitedAreas: MutableSet<String> = mutableSetOf()
    var time: Int = GameData.TIME_DAY
    var turns: Int = 0                                            // 探索回数
    var maxTurns: Int = 40                                        // これを超えるとTIME OVER
    var endingId: String? = null
    var clearCount: Int = 0
    var hintUsed: Int = 0
    var started: Boolean = false

    /** 見つけた欠片に対応する手がかりだけを返す */
    fun collectedClues(): List<GameData.Clue> =
        foundFragments.sorted().mapNotNull { clues.getOrNull(it) }

    fun newGame(ctx: Context, seedValue: Long? = null) {
        val g = if (seedValue != null) Puzzle.generate(seedValue) else Puzzle.generate()
        seed = g.seed
        treasureAreaId = g.treasureAreaId
        clues = g.clues.toMutableList()
        fragmentAreaIds = g.fragmentAreaIds.toMutableList()
        foundFragments = mutableSetOf()
        foundItems = mutableSetOf()
        visitedAreas = mutableSetOf()
        time = GameData.TIME_DAY
        turns = 0
        maxTurns = 40
        endingId = null
        hintUsed = 0
        started = true
        save(ctx)
    }

    fun save(ctx: Context) {
        val o = JSONObject()
        o.put("seed", seed)
        o.put("t", treasureAreaId)
        o.put("clues", JSONArray().also { arr ->
            clues.forEach { c ->
                arr.put(JSONObject().apply {
                    put("ty", c.type.name)
                    put("p", c.positive)
                    put("ex", c.excludeAreaId ?: "")
                })
            }
        })
        o.put("frag", JSONArray(fragmentAreaIds))
        o.put("found", JSONArray(foundFragments.toList()))
        o.put("items", JSONArray(foundItems.toList()))
        o.put("visited", JSONArray(visitedAreas.toList()))
        o.put("time", time)
        o.put("turns", turns)
        o.put("maxTurns", maxTurns)
        o.put("ending", endingId ?: "")
        o.put("clear", clearCount)
        o.put("hint", hintUsed)
        o.put("started", started)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, o.toString()).apply()
    }

    fun load(ctx: Context) {
        try {
            val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY, "") ?: ""
            if (raw.isBlank()) { started = false; return }
            val o = JSONObject(raw)
            seed = o.optLong("seed")
            treasureAreaId = o.optString("t")
            clues = mutableListOf()
            val ca = o.optJSONArray("clues")
            if (ca != null) for (i in 0 until ca.length()) {
                val c = ca.getJSONObject(i)
                val ex = c.optString("ex")
                clues.add(GameData.Clue(
                    GameData.ClueType.valueOf(c.optString("ty")),
                    c.optBoolean("p"),
                    if (ex.isBlank()) null else ex))
            }
            fragmentAreaIds = toStrList(o.optJSONArray("frag")).toMutableList()
            foundFragments = toIntList(o.optJSONArray("found")).toMutableSet()
            foundItems = toStrList(o.optJSONArray("items")).toMutableSet()
            visitedAreas = toStrList(o.optJSONArray("visited")).toMutableSet()
            time = o.optInt("time", GameData.TIME_DAY)
            turns = o.optInt("turns", 0)
            maxTurns = o.optInt("maxTurns", 40)
            endingId = o.optString("ending").ifBlank { null }
            clearCount = o.optInt("clear", 0)
            hintUsed = o.optInt("hint", 0)
            started = o.optBoolean("started", false)
        } catch (e: Exception) {
            started = false
        }
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        started = false
    }

    private fun toStrList(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until a.length()) out.add(a.optString(i))
        return out
    }

    private fun toIntList(a: JSONArray?): List<Int> {
        if (a == null) return emptyList()
        val out = mutableListOf<Int>()
        for (i in 0 until a.length()) out.add(a.optInt(i))
        return out
    }
}
