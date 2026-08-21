package com.appathy.mamoridx

/**
 * 『蒼海の秘宝 ～七つの羅針盤～』のゲームデータ定義。
 * 画像は res/drawable に置かれれば自動で使われ、無ければ色板で代替される。
 */
object GameData {

    // 時間帯
    const val TIME_DAY = 0
    const val TIME_NIGHT = 1

    /**
     * 探索エリア。
     * 各属性が謎解きの手がかり生成に使われる。
     */
    data class Area(
        val id: String,
        val name: String,
        val drawable: String,       // res/drawable の名前（拡張子なし）
        val flavor: String,         // 到着時の描写
        val seaside: Boolean,       // 海に面している
        val highGround: Boolean,    // 高い場所
        val manMade: Boolean,       // 人の手が入った場所
        val hasWater: Boolean,      // 真水がある
        val nightOnly: Boolean,     // 夜しか入れない
        val dayOnly: Boolean        // 昼しか入れない
    )

    val areas: List<Area> = listOf(
        Area("beach", "海岸", "area_beach",
            "白い砂に波が寄せる。打ち上げられた流木と貝殻が散らばっている。",
            seaside = true, highGround = false, manMade = false,
            hasWater = false, nightOnly = false, dayOnly = false),
        Area("forest", "森", "area_forest",
            "ざわめく緑の天蓋。葉の隙間から光が落ち、鳥の声が響く。",
            seaside = false, highGround = false, manMade = false,
            hasWater = false, nightOnly = false, dayOnly = true),
        Area("cave", "洞窟", "area_cave",
            "岩肌がひんやりと湿っている。奥から水の滴る音がする。",
            seaside = true, highGround = false, manMade = false,
            hasWater = true, nightOnly = false, dayOnly = false),
        Area("lighthouse", "灯台", "area_lighthouse",
            "赤白の塔が崖の上に立つ。潮風が強く吹き抜けていく。",
            seaside = true, highGround = true, manMade = true,
            hasWater = false, nightOnly = false, dayOnly = false),
        Area("volcano", "火山", "area_volcano",
            "黒い岩肌から熱気が立ちのぼる。山頂がかすかに赤く光っている。",
            seaside = false, highGround = true, manMade = false,
            hasWater = false, nightOnly = false, dayOnly = true),
        Area("temple", "神殿", "area_temple",
            "蔦に覆われた石の柱。苔むした階段の先に、暗い入口が口を開けている。",
            seaside = false, highGround = false, manMade = true,
            hasWater = false, nightOnly = false, dayOnly = false),
        Area("waterfall", "滝", "area_waterfall",
            "翡翠色の滝壺に水が落ち続ける。飛沫に小さな虹がかかっている。",
            seaside = false, highGround = true, manMade = false,
            hasWater = true, nightOnly = false, dayOnly = false),
        Area("pier", "桟橋", "area_pier",
            "潮に洗われた木の板が続く。舫われたカヌーが静かに揺れている。",
            seaside = true, highGround = false, manMade = true,
            hasWater = false, nightOnly = false, dayOnly = false),
        Area("ruins", "遺跡", "area_ruins",
            "砂に半ば埋もれた石柱。床に刻まれた円の文様が、まだかすかに読み取れる。",
            seaside = false, highGround = false, manMade = true,
            hasWater = false, nightOnly = true, dayOnly = false)
    )

    fun area(id: String): Area = areas.first { it.id == id }

    /** 羅針盤の欠片 */
    data class Fragment(
        val index: Int,
        val name: String,
        val drawable: String,
        val areaId: String          // どのエリアで見つかるか（初期配置。実際は毎回シャッフル）
    )

    val fragmentNames = listOf(
        "太陽の欠片" to "compass_1_sun",
        "月の欠片" to "compass_2_moon",
        "波の欠片" to "compass_3_wave",
        "炎の欠片" to "compass_4_flame",
        "葉の欠片" to "compass_5_leaf",
        "星の欠片" to "compass_6_star",
        "貝の欠片" to "compass_7_shell"
    )

    /** 収集アイテム（謎解きの直接の鍵ではないが、日誌と実績に関わる） */
    data class Item(val id: String, val name: String, val drawable: String, val note: String)

    val items: List<Item> = listOf(
        Item("scroll", "古びた巻物", "item_scroll",
            "文字はかすれているが、島の地形を描いた図が読み取れる。"),
        Item("lantern", "真鍮のランタン", "item_lantern",
            "まだ火が入る。暗い場所で役に立ちそうだ。"),
        Item("key", "貝細工の鍵", "item_key",
            "螺旋の意匠。どこかの錠前と対になっている。"),
        Item("shell", "大きな巻貝", "item_shell",
            "耳を当てると、波とは違う音が聞こえる気がする。"),
        Item("gem", "青い宝石", "item_gem",
            "光にかざすと、内側で小さな星が瞬く。"),
        Item("bottle", "手紙入りの瓶", "item_bottle",
            "コルクは固い。中の紙には短い言葉が書かれている。"),
        Item("telescope", "真鍮の望遠鏡", "item_telescope",
            "遠くを見るためのもの。高い場所でこそ意味を持つ。"),
        Item("journal", "航海士の日誌", "item_journal",
            "表紙に羅針盤の紋章。持ち主は最後まで島を離れなかったらしい。"),
        Item("map", "宝の地図", "item_map",
            "島の輪郭と、赤い×印。ただし印の位置は、いまの地形と合っていない。"),
        Item("stele", "小さな石碑", "item_stele",
            "渦巻きの文様が刻まれている。神殿の壁と同じ様式だ。")
    )

    // =========================================================
    // 出来事（探索中に起きる演出。謎解きの条件には影響しない）
    // =========================================================
    /**
     * @param timeReq -1=いつでも / TIME_DAY / TIME_NIGHT
     * @param rainReq null=不問 / true=雨のとき / false=雨でないとき
     */
    data class Happening(
        val id: String,
        val title: String,
        val drawable: String,
        val text: String,
        val timeReq: Int = -1,
        val rainReq: Boolean? = null
    )

    val happenings: List<Happening> = listOf(
        Happening("storm", "嵐が来た", "event_storm",
            "海が牙を剥いた。稲光が水平線を白く裂く。\n" +
            "こんな夜に船を出した者がいたのだろうか。",
            timeReq = -1, rainReq = true),
        Happening("tide", "潮が引いた", "event_tide",
            "波が退き、砂の下から石畳が現れた。\n" +
            "渦巻きの文様。誰かがここを歩いていた証だ。",
            timeReq = TIME_DAY, rainReq = false),
        Happening("constellation", "星がひとつの形を結ぶ", "event_constellation",
            "夜空の星が、ゆっくりと羅針盤の形に見えてきた。\n" +
            "見上げていると、進むべき方角がわかる気がする。",
            timeReq = TIME_NIGHT, rainReq = false),
        Happening("sunrise", "夜が明けた", "event_sunrise",
            "水平線から光が伸びてくる。\n" +
            "島の輪郭が、少しずつ戻ってきた。",
            timeReq = TIME_DAY, rainReq = false),
        Happening("rockfall", "岩が崩れた", "event_rockfall",
            "頭上で鈍い音がして、岩が転がり落ちてきた。\n" +
            "――間一髪。古い道は、もう古いままではいられないらしい。",
            timeReq = -1, rainReq = null),
        Happening("secretdoor", "隠された扉", "event_secretdoor",
            "蔦をかき分けると、岩肌に石の扉があった。\n" +
            "隙間から、暖かい光が漏れている。",
            timeReq = -1, rainReq = null)
    )

    /** 手がかりの種類 */
    enum class ClueType {
        SEASIDE, HIGH_GROUND, MAN_MADE, HAS_WATER, NIGHT_ONLY, DAY_ONLY, NOT_AREA
    }

    /**
     * 手がかり1つ。
     * positive=true なら「宝は〜である」、false なら「宝は〜ではない」。
     */
    data class Clue(val type: ClueType, val positive: Boolean, val excludeAreaId: String? = null) {
        fun text(): String = when (type) {
            ClueType.SEASIDE ->
                if (positive) "宝は、潮騒の聞こえる場所にある。"
                else "宝は、波の音が届かぬ場所にある。"
            ClueType.HIGH_GROUND ->
                if (positive) "宝は、島を見下ろす高みにある。"
                else "宝は、高みにはない。低きに眠る。"
            ClueType.MAN_MADE ->
                if (positive) "宝は、人の手が触れた場所にある。"
                else "宝は、人の手が及ばぬ自然の中にある。"
            ClueType.HAS_WATER ->
                if (positive) "宝は、真水の湧く場所にある。"
                else "宝は、真水のない場所にある。"
            ClueType.NIGHT_ONLY ->
                if (positive) "宝は、陽の下では決して姿を見せぬ。"
                else "宝は、夜を待たずとも辿り着ける。"
            ClueType.DAY_ONLY ->
                if (positive) "宝は、陽のあるうちにしか近づけぬ。"
                else "宝は、陽が落ちても閉ざされはしない。"
            ClueType.NOT_AREA ->
                "宝は、${excludeAreaId?.let { area(it).name } ?: "?"}にはない。"
        }

        fun matches(a: Area): Boolean = when (type) {
            ClueType.SEASIDE -> a.seaside == positive
            ClueType.HIGH_GROUND -> a.highGround == positive
            ClueType.MAN_MADE -> a.manMade == positive
            ClueType.HAS_WATER -> a.hasWater == positive
            ClueType.NIGHT_ONLY -> a.nightOnly == positive
            ClueType.DAY_ONLY -> a.dayOnly == positive
            ClueType.NOT_AREA -> a.id != excludeAreaId
        }
    }


    // =========================================================
    // 島の人々（NPC）
    // =========================================================
    /**
     * 各エリアに住人がいる。話しかけると、そのエリアの言い伝えを聞ける。
     * 証言は「まだ見つけていない欠片がどのエリアにあるか」を1回だけ教えてくれる。
     */
    data class Npc(
        val id: String,
        val name: String,
        val drawable: String,
        val areaId: String,
        val greeting: String,
        val lore: String
    )

    val npcs: List<Npc> = listOf(
        Npc("oldsailor", "老いた船乗り ハラウ", "npc_oldsailor", "pier",
            "「おう、見ない顔だな。この桟橋は昔、宝を積んだ船が最後に停まった場所さ」",
            "「羅針盤ってのはな、割れても方角を忘れねえ。欠片が七つ揃えば、島が自分から口を開くよ」"),
        Npc("girl", "島の少女 レイア", "npc_girl", "beach",
            "「こんにちは！ 果物いる？ ……あ、宝を探してるの？」",
            "「おじいちゃんが言ってた。宝は『触れられた場所』か『誰も触れない場所』か、どっちかだって」"),
        Npc("scholar", "学者 コルト", "npc_scholar", "ruins",
            "「静かに。……いま、床の文様を写しているところだ」",
            "「この遺跡は月の出ている間しか入れない。つまり、条件そのものが手がかりになる」"),
        Npc("fisher", "漁師 マノ", "npc_fisher", "cave",
            "「潮が引くとな、この洞窟の奥まで歩いていける。危ないから気をつけろよ」",
            "「真水が湧く場所は島に二つだけだ。覚えておいて損はない」"),
        Npc("child", "島の少年 タヴィ", "npc_child", "forest",
            "「ねえ、宝探してるんでしょ！ ぼくも探してるんだ！」",
            "「森の奥って、夜は入れないんだよ。だから夜のうちは別の場所を見たほうがいいと思う」"),
        Npc("keeper", "灯台守 ミナ", "npc_keeper", "lighthouse",
            "「ようこそ。ここからは島のほとんどが見渡せます」",
            "「高い場所は二つ。灯台と、あとひとつ。低い場所を探すなら、ここは外していい」"),
        Npc("parrot", "おしゃべりオウム ポポ", "npc_parrot", "waterfall",
            "「ポポ！ タカラ、ドコ？ タカラ、ドコ？」",
            "「ナナツ、ソロエロ！ ナナツ、ソロエロ！ ……ポポ、シッテル」")
    )

    fun npcAt(areaId: String): Npc? = npcs.firstOrNull { it.areaId == areaId }

    // =========================================================
    // 実績
    // =========================================================
    data class Achievement(val id: String, val name: String, val drawable: String,
                           val desc: String)

    val achievements: List<Achievement> = listOf(
        Achievement("first_step", "はじめの一歩", "ach_first_step",
            "最初のエリアを探索した"),
        Achievement("all_areas", "島を歩き尽くす", "ach_all_areas",
            "9つのエリアすべてを探索した"),
        Achievement("night_owl", "夜更かし", "ach_night_owl",
            "夜のあいだに探索した"),
        Achievement("all_compass", "七つの羅針盤", "ach_all_compass",
            "羅針盤の欠片を7つすべて集めた"),
        Achievement("speedrun", "最短の航路", "ach_speedrun",
            "20回以内の行動で宝に辿り着いた"),
        Achievement("solo", "ひとりの航海", "ach_solo",
            "ヒントを使わずに宝を見つけた"),
        Achievement("team", "島の人々と", "ach_team",
            "島の住人7人すべてと話した"),
        Achievement("true_end", "真の結末", "ach_true_end",
            "TRUE ENDに到達した")
    )

    /** エンディング */
    data class Ending(val id: String, val title: String, val drawable: String, val body: String)

    val endings = listOf(
        Ending("TRUE", "TRUE END", "end_true",
            "七つの欠片が一つの羅針盤に戻り、島の記憶がすべて繋がった。" +
            "航海士が守ろうとしたのは金銀ではなく、この島そのものだった。"),
        Ending("TREASURE", "TREASURE END", "end_treasure",
            "宝を掘り当てた。金貨と真珠が月明かりに光る。" +
            "ただ、羅針盤にはまだ欠けた場所が残っている。"),
        Ending("LEGEND", "LEGEND END", "end_legend",
            "石像が目を開き、島の伝説が動き出した。" +
            "あなたの名は、次の航海士たちに語り継がれるだろう。"),
        Ending("TIMEOVER", "TIME OVER", "end_timeover",
            "潮が満ち、霧が島を包んだ。入口は静かに閉じていく。" +
            "――また、次の満潮のときに。"),
        Ending("BETRAYAL", "BETRAYAL END", "end_betrayal",
            "宝だけを積んで、あなたは島を離れた。" +
            "羅針盤はほとんど欠けたまま、島の記憶は語られずに残る。" +
            "手にしたものは重い。それでも、何かを置いてきた気がする。"),
        Ending("SECRET", "SECRET END", "end_secret",
            "七つの欠片も、島に遺されたすべての品も、あなたの手の中にあった。" +
            "羅針盤が指したのは陸ではなく、海の底だった。" +
            "沈んだ神殿が、いま静かに光っている。――ここが、始まりの場所だ。")
    )
}
