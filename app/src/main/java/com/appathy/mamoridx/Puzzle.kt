package com.appathy.mamoridx

import kotlin.random.Random

/**
 * 毎回異なる謎を生成する。
 *
 * 設計の核:
 * ・宝は9エリアのいずれかにランダムに隠される
 * ・7つの羅針盤の欠片は、それぞれ1つの「手がかり」を持つ
 * ・手がかりは単体では答えを決めず、組み合わせて初めて1箇所に絞れる
 * ・生成後に必ず「答えが一意に定まるか」を検証してから採用する
 */
object Puzzle {

    data class Generated(
        val seed: Long,
        val treasureAreaId: String,
        val clues: List<GameData.Clue>,
        val fragmentAreaIds: List<String>   // 欠片i がどのエリアで見つかるか
    )

    /**
     * @param seed 同じseedなら同じ謎になる（周回・共有用）
     */
    fun generate(seed: Long = Random.nextLong()): Generated {
        val rnd = Random(seed)
        val areas = GameData.areas

        // 何度か試して、一意に定まる手がかりの組み合わせを探す
        repeat(200) {
            val treasure = areas[rnd.nextInt(areas.size)]
            val clues = buildClues(treasure, rnd)
            if (clues != null && solve(clues).size == 1 && solve(clues)[0].id == treasure.id) {
                return Generated(
                    seed = seed,
                    treasureAreaId = treasure.id,
                    clues = clues,
                    fragmentAreaIds = placeFragments(treasure.id, rnd)
                )
            }
        }

        // 万一生成に失敗した場合の保険（必ず解ける形にする）
        val treasure = areas[rnd.nextInt(areas.size)]
        val fallback = areas.filter { it.id != treasure.id }
            .shuffled(rnd).take(7)
            .map { GameData.Clue(GameData.ClueType.NOT_AREA, false, it.id) }
        return Generated(seed, treasure.id, fallback, placeFragments(treasure.id, rnd))
    }

    /** 正解エリアに矛盾しない手がかりを7つ作る */
    private fun buildClues(t: GameData.Area, rnd: Random): List<GameData.Clue>? {
        val pool = mutableListOf<GameData.Clue>()

        // 属性系の手がかり（正解の実際の属性をそのまま述べる＝嘘はつかない）
        pool.add(GameData.Clue(GameData.ClueType.SEASIDE, t.seaside))
        pool.add(GameData.Clue(GameData.ClueType.HIGH_GROUND, t.highGround))
        pool.add(GameData.Clue(GameData.ClueType.MAN_MADE, t.manMade))
        pool.add(GameData.Clue(GameData.ClueType.HAS_WATER, t.hasWater))
        pool.add(GameData.Clue(GameData.ClueType.NIGHT_ONLY, t.nightOnly))
        pool.add(GameData.Clue(GameData.ClueType.DAY_ONLY, t.dayOnly))

        // 除外系の手がかり（正解以外のエリアを名指しで除く）
        val others = GameData.areas.filter { it.id != t.id }.shuffled(rnd)
        for (o in others) {
            pool.add(GameData.Clue(GameData.ClueType.NOT_AREA, false, o.id))
        }

        // 属性系を優先しつつ、足りない分を除外系で補って7つにする
        val attrs = pool.take(6).shuffled(rnd)
        val excludes = pool.drop(6)

        // まず属性系だけでどこまで絞れるか見る
        for (attrCount in 6 downTo 2) {
            val chosen = attrs.take(attrCount).toMutableList()
            val need = 7 - attrCount
            chosen.addAll(excludes.take(need))
            if (solve(chosen).size == 1) return chosen.shuffled(rnd)
        }
        return null
    }

    /** 手がかりの集合から、条件を満たすエリアを求める */
    fun solve(clues: List<GameData.Clue>): List<GameData.Area> =
        GameData.areas.filter { a -> clues.all { it.matches(a) } }

    /** 集めた手がかりだけで、現時点の候補を求める */
    fun candidates(collectedClues: List<GameData.Clue>): List<GameData.Area> =
        if (collectedClues.isEmpty()) GameData.areas else solve(collectedClues)

    /** 欠片を9エリアに散らす（正解エリアにも1つは置く） */
    private fun placeFragments(treasureId: String, rnd: Random): List<String> {
        val ids = GameData.areas.map { it.id }.shuffled(rnd).toMutableList()
        val out = mutableListOf<String>()
        out.add(treasureId)
        for (id in ids) {
            if (out.size >= 7) break
            if (id == treasureId) continue
            out.add(id)
        }
        // 7個に満たない場合は重複を許して埋める
        while (out.size < 7) out.add(ids[rnd.nextInt(ids.size)])
        return out.shuffled(rnd)
    }

    /**
     * 段階ヒント。
     * @param level 1=方向づけ / 2=絞り込み / 3=ほぼ答え
     */
    fun hint(g: Generated, collectedClues: List<GameData.Clue>, level: Int): String {
        val cands = candidates(collectedClues)
        val t = GameData.area(g.treasureAreaId)
        return when (level) {
            1 -> if (collectedClues.isEmpty())
                "まずは羅針盤の欠片を集めよう。欠片ひとつひとつが、宝の在り処を一言ずつ語ってくれる。"
            else
                "今ある手がかりだけで、候補は ${cands.size} 箇所まで絞れている。日誌を見返してみよう。"
            2 -> {
                val attr = when {
                    t.seaside -> "潮の音が聞こえるかどうか"
                    t.highGround -> "島を見下ろす高みかどうか"
                    t.manMade -> "人の手が入っているかどうか"
                    else -> "自然のままかどうか"
                }
                "注目すべきは「$attr」。この一点で、候補は大きく減るはずだ。"
            }
            else -> {
                val other = cands.firstOrNull { it.id != t.id }
                if (other != null) "${other.name}ではない。そこは条件のひとつを満たしていない。"
                else "答えはもう目の前だ。${t.name}を調べてみるといい。"
            }
        }
    }
}
