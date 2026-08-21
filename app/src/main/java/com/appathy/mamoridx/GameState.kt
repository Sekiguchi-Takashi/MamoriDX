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
    var raining: Boolean = false
    var dusk: Boolean = false          // 夜になった直後の宵の口（見た目のみ）
    var turns: Int = 0                                            // 探索回数
    var maxTurns: Int = 40                                        // これを超えるとTIME OVER
    var endingId: String? = null
    var clearCount: Int = 0
    var hintUsed: Int = 0
    var started: Boolean = false
    var talkedNpcs: MutableSet<String> = mutableSetOf()   // 話したNPC
    var toldNpcs: MutableSet<String> = mutableSetOf()     // 証言をもらったNPC
    var achievements: MutableSet<String> = mutableSetOf() // 解除済み実績（周回で保持）

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
        raining = false
        dusk = false
        turns = 0
        maxTurns = 40
        endingId = null
        hintUsed = 0
        talkedNpcs = mutableSetOf()
        toldNpcs = mutableSetOf()
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
        o.put("rain", raining)
        o.put("dusk", dusk)
        o.put("turns", turns)
        o.put("maxTurns", maxTurns)
        o.put("ending", endingId ?: "")
        o.put("clear", clearCount)
        o.put("hint", hintUsed)
        o.put("started", started)
        o.put("talked", JSONArray(talkedNpcs.toList()))
        o.put("told", JSONArray(toldNpcs.toList()))
        o.put("ach", JSONArray(achievements.toList()))
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
            raining = o.optBoolean("rain", false)
            dusk = o.optBoolean("dusk", false)
            turns = o.optInt("turns", 0)
            maxTurns = o.optInt("maxTurns", 40)
            endingId = o.optString("ending").ifBlank { null }
            clearCount = o.optInt("clear", 0)
            hintUsed = o.optInt("hint", 0)
            started = o.optBoolean("started", false)
            talkedNpcs = toStrList(o.optJSONArray("talked")).toMutableSet()
            toldNpcs = toStrList(o.optJSONArray("told")).toMutableSet()
            achievements = toStrList(o.optJSONArray("ach")).toMutableSet()
        } catch (e: Exception) {
            started = false
        }
    }

    /** 状況から実績を判定して解除する。戻り値は新たに解除されたもの */
    fun refreshAchievements(ctx: Context): List<GameData.Achievement> {
        val newly = mutableListOf<GameData.Achievement>()
        fun unlock(id: String, cond: Boolean) {
            if (cond && !achievements.contains(id)) {
                achievements.add(id)
                GameData.achievements.firstOrNull { it.id == id }?.let { newly.add(it) }
            }
        }
        unlock("first_step", visitedAreas.isNotEmpty())
        unlock("all_areas", visitedAreas.size >= GameData.areas.size)
        unlock("night_owl", time == GameData.TIME_NIGHT && visitedAreas.isNotEmpty())
        unlock("all_compass", foundFragments.size >= 7)
        unlock("team", talkedNpcs.size >= GameData.npcs.size)
        val cleared = endingId != null && endingId != "TIMEOVER"
        unlock("speedrun", cleared && turns <= 20)
        unlock("solo", cleared && hintUsed == 0)
        unlock("true_end", endingId == "TRUE")
        if (newly.isNotEmpty()) save(ctx)
        return newly
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
