package com.appathy.mamoridx

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

/**
 * 南国ゲーム『蒼海の秘宝 ～七つの羅針盤～』
 *
 * 画面: タイトル / 島マップ / エリア探索 / 日誌 / 推理 / エンディング
 * 画像が未配置でも Art が色板を生成するため完全に動作する。
 */
class MainActivity : Activity() {

    // ===== 配色（アイコンの南国パレットに合わせる） =====
    private val bgColor = Color.parseColor("#FDF6E3")      // クリーム
    private val panelColor = Color.parseColor("#FFFFFF")
    private val inkColor = Color.parseColor("#2E4057")     // 濃紺
    private val subColor = Color.parseColor("#6B8299")
    private val seaColor = Color.parseColor("#29B6D8")     // ターコイズ
    private val sandColor = Color.parseColor("#F2C14E")    // 砂・金
    private val leafColor = Color.parseColor("#4CAF50")
    private val coralColor = Color.parseColor("#F26D6D")
    private val nightColor = Color.parseColor("#2A3A66")

    private lateinit var rootView: FrameLayout

    // 画面ID
    private val SC_TITLE = 0
    private val SC_MAP = 1
    private val SC_AREA = 2
    private val SC_JOURNAL = 3
    private val SC_DEDUCE = 4
    private val SC_ENDING = 5
    private val SC_ACHIEVE = 6

    private var screen = SC_TITLE
    private var currentAreaId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GameState.load(applicationContext)
        rootView = FrameLayout(this).apply { setBackgroundColor(bgColor) }
        setContentView(rootView)
        show(SC_TITLE)
    }

    override fun onBackPressed() {
        when (screen) {
            SC_AREA, SC_JOURNAL, SC_DEDUCE -> show(SC_MAP)
            SC_ACHIEVE -> show(SC_TITLE)
            SC_MAP -> show(SC_TITLE)
            else -> super.onBackPressed()
        }
    }

    private fun show(s: Int) {
        screen = s
        rootView.removeAllViews()
        rootView.addView(
            when (s) {
                SC_TITLE -> buildTitle()
                SC_MAP -> buildMap()
                SC_AREA -> buildArea()
                SC_JOURNAL -> buildJournal()
                SC_DEDUCE -> buildDeduce()
                else -> buildEnding()
            }
        )
    }

    // =========================================================
    // タイトル
    // =========================================================
    private fun buildTitle(): View {
        val col = column()

        col.addView(imageBanner("ui_title", "南の島", 16 to 9))

        col.addView(TextView(this).apply {
            text = "蒼海の秘宝"
            textSize = 30f
            setTypeface(null, Typeface.BOLD)
            setTextColor(inkColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        })
        col.addView(TextView(this).apply {
            text = "～ 七つの羅針盤 ～"
            textSize = 15f
            setTextColor(seaColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(16))
        })

        if (GameState.started && GameState.endingId == null) {
            col.addView(bigButton("冒険を続ける", seaColor) { show(SC_MAP) })
            col.addView(bigButton("最初からはじめる", sandColor) { confirmNewGame() })
        } else {
            col.addView(bigButton("冒険をはじめる", seaColor) {
                GameState.newGame(applicationContext)
                showStory()
            })
            if (GameState.started) {
                col.addView(bigButton("前回の結果を見る", sandColor) { show(SC_ENDING) })
            }
        }

        col.addView(flatButton("実績を見る") { show(SC_ACHIEVE) })

        col.addView(card().apply {
            addView(head("あそびかた"))
            addView(body(
                "この島のどこかに、古い航海士が遺した宝が眠っています。\n\n" +
                "・9つのエリアを探索して、羅針盤の欠片を7つ集めます\n" +
                "・欠片はそれぞれ、宝の在り処を一言だけ教えてくれます\n" +
                "・欠片ひとつでは答えは出ません。組み合わせて考えます\n" +
                "・場所がわかったら「推理する」で答えを告げてください\n\n" +
                "宝の在り処は遊ぶたびに変わります。"))
        })

        if (GameState.clearCount > 0) {
            col.addView(card().apply {
                addView(head("記録"))
                addView(body("クリア回数: ${GameState.clearCount} 回"))
            })
        }

        return scroll(col)
    }

    private fun confirmNewGame() {
        AlertDialog.Builder(this)
            .setTitle("確認")
            .setMessage("いまの冒険の記録は消えます。最初からはじめますか？")
            .setPositiveButton("はじめる") { _, _ ->
                GameState.newGame(applicationContext)
                showStory()
            }
            .setNegativeButton("やめる", null)
            .show()
    }

    private fun showStory() {
        AlertDialog.Builder(this)
            .setTitle("蒼海の秘宝")
            .setMessage(
                "潮に流れ着いたあなたが浜辺で拾ったのは、割れた羅針盤の欠片だった。\n\n" +
                "裏には、こう刻まれている。\n" +
                "『七つ揃えよ。さすれば島が語りだす』\n\n" +
                "――さあ、探索をはじめよう。")
            .setPositiveButton("島へ向かう") { _, _ -> show(SC_MAP) }
            .setCancelable(false)
            .show()
    }

    // =========================================================
    // 島マップ
    // =========================================================
    private fun buildMap(): View {
        val col = column()

        col.addView(statusBar())
        col.addView(imageBannerChain(listOf("ui_island"), "南の島", 16 to 9))

        col.addView(TextView(this).apply {
            text = "どこを探索する？"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(inkColor)
            setPadding(0, dp(10), 0, dp(6))
        })

        GameData.areas.forEach { a ->
            val locked = (a.nightOnly && GameState.time != GameData.TIME_NIGHT) ||
                (a.dayOnly && GameState.time != GameData.TIME_DAY)
            val visited = GameState.visitedAreas.contains(a.id)
            val fragHere = GameState.fragmentAreaIds.withIndex()
                .any { (i, id) -> id == a.id && !GameState.foundFragments.contains(i) }

            col.addView(card().apply {
                if (locked) alpha = 0.5f
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(TextView(this@MainActivity).apply {
                        text = a.name
                        textSize = 18f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(if (locked) subColor else inkColor)
                        layoutParams = LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = when {
                            locked && a.nightOnly -> "夜のみ"
                            locked && a.dayOnly -> "昼のみ"
                            visited -> "探索済"
                            else -> "未探索"
                        }
                        textSize = 12f
                        setTextColor(if (visited) leafColor else subColor)
                    })
                })
                addView(TextView(this@MainActivity).apply {
                    text = attrLine(a)
                    textSize = 12f
                    setTextColor(subColor)
                    setPadding(0, dp(4), 0, 0)
                })
                if (fragHere && visited) {
                    addView(TextView(this@MainActivity).apply {
                        text = "◆ まだ何かありそうだ"
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(sandColor)
                        setPadding(0, dp(4), 0, 0)
                    })
                }
                if (!locked) {
                    setOnClickListener {
                        currentAreaId = a.id
                        show(SC_AREA)
                    }
                }
            })
        }

        col.addView(bigButton("日誌を見る（手がかり）", sandColor) { show(SC_JOURNAL) })
        col.addView(bigButton("推理する", coralColor) { show(SC_DEDUCE) })
        col.addView(bigButton(
            if (GameState.time == GameData.TIME_DAY) "夜を待つ" else "朝を待つ",
            if (GameState.time == GameData.TIME_DAY) nightColor else seaColor) {
            advanceTime()
        })
        col.addView(flatButton("タイトルへ") { show(SC_TITLE) })

        return scroll(col)
    }

    private fun attrLine(a: GameData.Area): String {
        val t = mutableListOf<String>()
        t.add(if (a.seaside) "海に面する" else "内陸")
        if (a.highGround) t.add("高所")
        if (a.manMade) t.add("人の手が入っている") else t.add("自然のまま")
        if (a.hasWater) t.add("真水がある")
        return t.joinToString("・")
    }

    private fun statusBar(): View = card().apply {
        setBackgroundColor(if (GameState.time == GameData.TIME_NIGHT)
            nightColor else seaColor)
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(this@MainActivity).apply {
                text = (if (GameState.time == GameData.TIME_DAY) "☀ 昼" else "☾ 夜") +
                    (if (GameState.raining) "　☂ 雨" else "")
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = "羅針盤 ${GameState.foundFragments.size}/7"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
        })
        addView(TextView(this@MainActivity).apply {
            text = "残り行動 ${GameState.maxTurns - GameState.turns} 回　" +
                "所持品 ${GameState.foundItems.size}/${GameData.items.size}"
            textSize = 12f
            setTextColor(Color.argb(220, 255, 255, 255))
            setPadding(0, dp(4), 0, 0)
        })
    }

    private fun advanceTime() {
        GameState.time =
            if (GameState.time == GameData.TIME_DAY) GameData.TIME_NIGHT else GameData.TIME_DAY
        // 天候は見た目のみ。謎解きの条件には影響しない
        GameState.raining =
            Random(GameState.seed + GameState.turns * 7L).nextInt(100) < 30
        GameState.turns++
        GameState.save(applicationContext)
        if (checkTimeOver()) return
        show(SC_MAP)
    }

    private fun checkTimeOver(): Boolean {
        if (GameState.turns >= GameState.maxTurns && GameState.endingId == null) {
            GameState.endingId = "TIMEOVER"
            GameState.save(applicationContext)
            show(SC_ENDING)
            return true
        }
        return false
    }

    // =========================================================
    // エリア探索
    // =========================================================
    private fun buildArea(): View {
        val a = GameData.area(currentAreaId)
        val col = column()

        col.addView(imageBannerChain(areaImageNames(a), a.name))

        col.addView(TextView(this).apply {
            text = a.name
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(inkColor)
            setPadding(0, dp(10), 0, dp(4))
        })
        col.addView(TextView(this).apply {
            text = a.flavor + if (GameState.raining) "\n雨が降っている。足元がぬかるんでいる。" else ""
            textSize = 14f
            setTextColor(inkColor)
            setPadding(0, 0, 0, dp(8))
        })

        col.addView(bigButton("あたりを調べる", leafColor) { explore(a) })

        // この土地の住人
        val npc = GameData.npcAt(a.id)
        if (npc != null) {
            col.addView(card().apply {
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(ImageView(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(90), dp(120))
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(Art.get(this@MainActivity, npc.drawable,
                            npc.name, 240, 320))
                    })
                    addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            leftMargin = dp(10)
                        }
                        addView(TextView(this@MainActivity).apply {
                            text = npc.name
                            textSize = 15f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(inkColor)
                        })
                        addView(TextView(this@MainActivity).apply {
                            text = npc.greeting
                            textSize = 12f
                            setTextColor(subColor)
                            setPadding(0, dp(4), 0, 0)
                        })
                    })
                })
                addView(Button(this@MainActivity).apply {
                    text = if (GameState.toldNpcs.contains(npc.id))
                        "もう一度話しかける" else "話しかける（行動を消費しない）"
                    textSize = 14f
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    setBackgroundColor(sandColor)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
                    ).apply { topMargin = dp(8) }
                    setOnClickListener { talkTo(npc) }
                })
            })
        }

        col.addView(flatButton("島マップへ戻る") { show(SC_MAP) })

        // このエリアで見つけたもの
        val foundHere = GameState.fragmentAreaIds.withIndex()
            .filter { (i, id) -> id == a.id && GameState.foundFragments.contains(i) }
        if (foundHere.isNotEmpty()) {
            col.addView(card().apply {
                addView(head("ここで見つけたもの"))
                foundHere.forEach { (i, _) ->
                    addView(body("◆ ${GameData.fragmentNames[i].first}"))
                }
            })
        }

        return scroll(col)
    }

    private fun explore(a: GameData.Area) {
        GameState.turns++
        GameState.visitedAreas.add(a.id)
        GameState.refreshAchievements(applicationContext)

        // このエリアに未発見の欠片があるか
        val fragIdx = GameState.fragmentAreaIds.withIndex()
            .firstOrNull { (i, id) -> id == a.id && !GameState.foundFragments.contains(i) }
            ?.index

        if (fragIdx != null) {
            GameState.foundFragments.add(fragIdx)
            GameState.save(applicationContext)
            val name = GameData.fragmentNames[fragIdx].first
            val clue = GameState.clues.getOrNull(fragIdx)
            AlertDialog.Builder(this)
                .setTitle("$name を見つけた！")
                .setMessage(
                    "欠片に刻まれた言葉が、頭の中に流れ込んでくる。\n\n" +
                    "『${clue?.text() ?: "……"}』\n\n" +
                    "（日誌に書き留めた）")
                .setPositiveButton("OK") { _, _ ->
                    if (!checkTimeOver()) show(SC_AREA)
                }
                .setCancelable(false)
                .show()
            return
        }

        // アイテム発見
        val rnd = Random(GameState.seed + GameState.turns * 31L + a.id.hashCode())
        val remaining = GameData.items.filter { !GameState.foundItems.contains(it.id) }
        if (remaining.isNotEmpty() && rnd.nextInt(100) < 45) {
            val item = remaining[rnd.nextInt(remaining.size)]
            GameState.foundItems.add(item.id)
            GameState.save(applicationContext)
            AlertDialog.Builder(this)
                .setTitle("${item.name} を見つけた")
                .setMessage(item.note)
                .setPositiveButton("OK") { _, _ ->
                    if (!checkTimeOver()) show(SC_AREA)
                }
                .show()
            return
        }

        GameState.save(applicationContext)
        val misses = listOf(
            "めぼしいものは見当たらない。",
            "砂と石ばかりだ。だが、この場所の様子は覚えておこう。",
            "風の音だけが返ってくる。",
            "何かがあった痕跡はある。もう遅かったようだ。"
        )
        Toast.makeText(this, misses[rnd.nextInt(misses.size)], Toast.LENGTH_SHORT).show()
        if (!checkTimeOver()) show(SC_AREA)
    }

    /** 住人に話しかける。行動は消費しない */
    private fun talkTo(npc: GameData.Npc) {
        GameState.talkedNpcs.add(npc.id)

        val sb = StringBuilder()
        sb.append(npc.lore)

        // 初回だけ、まだ見つけていない欠片の在り処を1つ教えてくれる
        if (!GameState.toldNpcs.contains(npc.id)) {
            val unfound = GameState.fragmentAreaIds.withIndex()
                .filter { (i, _) -> !GameState.foundFragments.contains(i) }
                .map { (_, id) -> id }
                .filter { it != npc.areaId }
                .distinct()
            if (unfound.isNotEmpty()) {
                val pick = unfound[Random(GameState.seed + npc.id.hashCode())
                    .nextInt(unfound.size)]
                sb.append("\n\n「そういえば、")
                sb.append(GameData.area(pick).name)
                sb.append("のあたりで、光るものを見かけたよ」")
                GameState.toldNpcs.add(npc.id)
            }
        }

        GameState.save(applicationContext)
        val newly = GameState.refreshAchievements(applicationContext)

        AlertDialog.Builder(this)
            .setTitle(npc.name)
            .setMessage(sb.toString())
            .setPositiveButton("ありがとう") { _, _ ->
                if (newly.isNotEmpty()) showAchievementToast(newly)
                show(SC_AREA)
            }
            .show()
    }

    private fun showAchievementToast(list: List<GameData.Achievement>) {
        Toast.makeText(this,
            "実績を解除: " + list.joinToString("、") { it.name },
            Toast.LENGTH_LONG).show()
    }

    // =========================================================
    // 実績
    // =========================================================
    private fun buildAchieve(): View {
        val col = column()
        GameState.refreshAchievements(applicationContext)

        col.addView(TextView(this).apply {
            text = "実績"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(inkColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        })
        val got = GameData.achievements.count { GameState.achievements.contains(it.id) }
        col.addView(TextView(this).apply {
            text = "$got / ${GameData.achievements.size} 解除"
            textSize = 14f
            setTextColor(seaColor)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })

        GameData.achievements.forEach { a ->
            val unlocked = GameState.achievements.contains(a.id)
            col.addView(card().apply {
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(ImageView(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        alpha = if (unlocked) 1f else 0.25f
                        setImageDrawable(Art.get(this@MainActivity, a.drawable,
                            a.name, 200, 200))
                    })
                    addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            leftMargin = dp(12)
                        }
                        addView(TextView(this@MainActivity).apply {
                            text = if (unlocked) a.name else "？？？"
                            textSize = 16f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(if (unlocked) sandColor else subColor)
                        })
                        addView(TextView(this@MainActivity).apply {
                            text = a.desc
                            textSize = 12f
                            setTextColor(if (unlocked) inkColor else subColor)
                            setPadding(0, dp(4), 0, 0)
                        })
                    })
                })
            })
        }

        col.addView(flatButton("タイトルへ") { show(SC_TITLE) })
        return scroll(col)
    }

    // =========================================================
    // 日誌
    // =========================================================
    private fun buildJournal(): View {
        val col = column()
        col.addView(statusBar())

        col.addView(card().apply {
            addView(head("羅針盤の欠片 ${GameState.foundFragments.size}/7"))
            GameData.fragmentNames.forEachIndexed { i, (name, _) ->
                val got = GameState.foundFragments.contains(i)
                addView(TextView(this@MainActivity).apply {
                    text = if (got) "◆ $name" else "◇ ？？？"
                    textSize = 15f
                    setTypeface(null, if (got) Typeface.BOLD else Typeface.NORMAL)
                    setTextColor(if (got) sandColor else subColor)
                    setPadding(0, dp(4), 0, 0)
                })
                if (got) {
                    addView(TextView(this@MainActivity).apply {
                        text = "　『${GameState.clues.getOrNull(i)?.text() ?: ""}』"
                        textSize = 13f
                        setTextColor(inkColor)
                    })
                }
            }
        })

        val cands = Puzzle.candidates(GameState.collectedClues())
        col.addView(card().apply {
            addView(head("いまの候補"))
            addView(body(
                if (GameState.foundFragments.isEmpty())
                    "まだ手がかりがない。9箇所すべてが候補だ。"
                else "手がかりを満たす場所は ${cands.size} 箇所。\n\n" +
                    cands.joinToString("\n") { "・${it.name}" }))
        })

        if (GameState.foundItems.isNotEmpty()) {
            col.addView(card().apply {
                addView(head("持ち物"))
                GameData.items.filter { GameState.foundItems.contains(it.id) }.forEach {
                    addView(TextView(this@MainActivity).apply {
                        text = "・${it.name}"
                        textSize = 14f
                        setTextColor(inkColor)
                        setPadding(0, dp(3), 0, 0)
                    })
                }
            })
        }

        col.addView(bigButton("ヒントをもらう", seaColor) { giveHint() })
        col.addView(flatButton("島マップへ戻る") { show(SC_MAP) })
        return scroll(col)
    }

    private fun giveHint() {
        val level = (GameState.hintUsed % 3) + 1
        GameState.hintUsed++
        GameState.save(applicationContext)
        val g = Puzzle.Generated(
            GameState.seed, GameState.treasureAreaId,
            GameState.clues, GameState.fragmentAreaIds)
        AlertDialog.Builder(this)
            .setTitle("AIガイドの助言（${level}段階目）")
            .setMessage(Puzzle.hint(g, GameState.collectedClues(), level))
            .setPositiveButton("ありがとう", null)
            .show()
    }

    // =========================================================
    // 推理
    // =========================================================
    private fun buildDeduce(): View {
        val col = column()
        col.addView(statusBar())

        col.addView(card().apply {
            addView(head("宝の在り処を告げる"))
            addView(body(
                "集めた手がかりから、宝が眠る場所をひとつ選んでください。\n" +
                "間違えると行動を1回消費します。"))
        })

        val cands = Puzzle.candidates(GameState.collectedClues())
        GameData.areas.forEach { a ->
            val fits = cands.any { it.id == a.id }
            col.addView(Button(this).apply {
                text = a.name + if (fits) "" else "（手がかりと矛盾）"
                textSize = 16f
                isAllCaps = false
                setTextColor(if (fits) Color.WHITE else subColor)
                setBackgroundColor(if (fits) seaColor else Color.parseColor("#E4E8EB"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(50)
                ).apply { topMargin = dp(8) }
                setOnClickListener { answer(a) }
            })
        }

        col.addView(flatButton("島マップへ戻る") { show(SC_MAP) })
        return scroll(col)
    }

    private fun answer(a: GameData.Area) {
        if (a.id == GameState.treasureAreaId) {
            val all = GameState.foundFragments.size >= 7
            GameState.endingId = when {
                all && GameState.hintUsed == 0 -> "TRUE"
                all -> "LEGEND"
                else -> "TREASURE"
            }
            GameState.clearCount++
            GameState.save(applicationContext)
            GameState.refreshAchievements(applicationContext)
            show(SC_ENDING)
        } else {
            GameState.turns++
            GameState.save(applicationContext)
            AlertDialog.Builder(this)
                .setTitle("……何もない")
                .setMessage("${a.name}を隅まで掘り返したが、宝の気配はなかった。\n" +
                    "手がかりを読み直そう。")
                .setPositiveButton("戻る") { _, _ ->
                    if (!checkTimeOver()) show(SC_DEDUCE)
                }
                .show()
        }
    }

    // =========================================================
    // エンディング
    // =========================================================
    private fun buildEnding(): View {
        val col = column()
        val e = GameData.endings.firstOrNull { it.id == GameState.endingId }
            ?: GameData.endings.last()

        col.addView(imageBanner(e.drawable, e.title, 16 to 9))

        col.addView(TextView(this).apply {
            text = e.title
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (e.id == "TIMEOVER") subColor else sandColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        })
        col.addView(TextView(this).apply {
            text = e.body
            textSize = 15f
            setTextColor(inkColor)
            setPadding(0, 0, 0, dp(10))
        })

        col.addView(card().apply {
            addView(head("この冒険の記録"))
            addView(body(
                "宝の在り処: ${GameData.area(GameState.treasureAreaId).name}\n" +
                "集めた欠片: ${GameState.foundFragments.size}/7\n" +
                "見つけた品: ${GameState.foundItems.size}/${GameData.items.size}\n" +
                "使った行動: ${GameState.turns} 回\n" +
                "ヒント使用: ${GameState.hintUsed} 回\n" +
                "この島の番号: ${GameState.seed}"))
        })

        if (GameState.endingId == "TREASURE") {
            col.addView(card().apply {
                addView(head("もっと先へ"))
                addView(body(
                    "七つの欠片をすべて集めてから宝に辿り着くと、島は別の顔を見せます。" +
                    "ヒントを使わずに解ければ、さらにその先が待っています。"))
            })
        }

        col.addView(bigButton("新しい島へ（もう一度遊ぶ）", seaColor) {
            GameState.newGame(applicationContext)
            showStory()
        })
        col.addView(flatButton("実績を見る") { show(SC_ACHIEVE) })
        col.addView(flatButton("タイトルへ") { show(SC_TITLE) })
        return scroll(col)
    }

    // =========================================================
    // UI部品
    // =========================================================
    private fun column(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(28))
    }

    private fun scroll(v: View): ScrollView = ScrollView(this).apply {
        setBackgroundColor(bgColor)
        addView(v)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(panelColor)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(8), 0, 0) }
    }

    private fun head(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 15f
        setTypeface(null, Typeface.BOLD)
        setTextColor(seaColor)
    }

    private fun body(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(inkColor)
        setLineSpacing(dp(2).toFloat(), 1f)
        setPadding(0, dp(6), 0, 0)
    }

    private fun bigButton(label: String, bg: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)
            ).apply { setMargins(0, dp(10), 0, 0) }
            setOnClickListener { onClick() }
        }

    private fun flatButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(subColor)
            setBackgroundColor(Color.parseColor("#E9EEF2"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
            ).apply { setMargins(0, dp(8), 0, 0) }
            setOnClickListener { onClick() }
        }

    /** エリア画像の候補名（天候→時間→通常 の順にフォールバック） */
    private fun areaImageNames(a: GameData.Area): List<String> {
        val base = a.drawable
        val night = GameState.time == GameData.TIME_NIGHT
        val out = mutableListOf<String>()
        if (GameState.raining) {
            out.add("${base}_rain")
            if (night) out.add("${base}_night")
        } else if (night) {
            out.add("${base}_night")
        }
        // 夜の絵が無いエリアは、昼の絵より夕暮れの絵のほうが自然
        if (night) out.add("${base}_dusk")
        out.add(base)
        return out
    }

    /** 画像バナー。画像未配置なら色板が入る */
    private fun imageBanner(name: String, label: String, ratio: Pair<Int, Int>): View =
        imageBannerChain(listOf(name), label, ratio)

    private fun imageBannerChain(names: List<String>, label: String,
                                 ratio: Pair<Int, Int> = 4 to 3): View {
        val w = resources.displayMetrics.widthPixels - dp(28)
        val h = (w * ratio.second / ratio.first)
        return ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, h)
            // ステッカー絵は正方形なので、切らずに収める
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(Art.getFirst(this@MainActivity, names, label,
                w.coerceAtLeast(360), h.coerceAtLeast(240)))
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
