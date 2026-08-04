package com.appathy.mamoridx

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    // ===== 配色（Appathyダーク基調） =====
    private val bgColor = Color.parseColor("#121212")
    private val cardColor = Color.parseColor("#1E1E1E")
    private val textColor = Color.parseColor("#EEEEEE")
    private val subColor = Color.parseColor("#9E9E9E")
    private val greenColor = Color.parseColor("#4CAF50")
    private val yellowColor = Color.parseColor("#FFC107")
    private val redColor = Color.parseColor("#F44336")
    private val accentColor = Color.parseColor("#03A9F4")

    private lateinit var contentArea: FrameLayout
    private lateinit var tabButtons: List<Button>

    // ===== 危険権限リスト（リスク加点対象） =====
    private val dangerousPerms = linkedMapOf(
        "android.permission.READ_CONTACTS" to "連絡先の読み取り",
        "android.permission.CAMERA" to "カメラ",
        "android.permission.RECORD_AUDIO" to "マイク",
        "android.permission.ACCESS_FINE_LOCATION" to "位置情報（精密）",
        "android.permission.ACCESS_COARSE_LOCATION" to "位置情報（おおよそ）",
        "android.permission.READ_SMS" to "SMSの読み取り",
        "android.permission.SEND_SMS" to "SMSの送信",
        "android.permission.READ_CALL_LOG" to "通話履歴の読み取り",
        "android.permission.READ_PHONE_STATE" to "電話の状態",
        "android.permission.READ_EXTERNAL_STORAGE" to "ストレージの読み取り",
        "android.permission.WRITE_EXTERNAL_STORAGE" to "ストレージへの書き込み",
        "android.permission.READ_MEDIA_IMAGES" to "画像の読み取り",
        "android.permission.READ_CALENDAR" to "カレンダーの読み取り",
        "android.permission.BODY_SENSORS" to "身体センサー"
    )

    data class AppRisk(
        val label: String,
        val packageName: String,
        val installer: String,
        val isSideloaded: Boolean,
        val grantedDangerous: List<String>,
        val score: Int,
        val classification: Int // 0=許可済み 1=要監視 2=要対策
    )

    private var cachedApps: List<AppRisk>? = null

    private val VPN_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DnsLogStore.load(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }

        // ===== ヘッダー =====
        root.addView(TextView(this).apply {
            text = "守りのDX 2.0"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(20), dp(16), dp(4))
        })
        root.addView(TextView(this).apply {
            text = "見つけて消す、から　見つけて守る、へ"
            textSize = 12f
            setTextColor(subColor)
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), dp(12))
        })

        // ===== 大分類タブ（5つ） =====
        val tabNames = listOf("状態", "通信", "診断", "PC", "説明書")
        tabButtons = tabNames.mapIndexed { index, name ->
            Button(this).apply {
                text = name
                textSize = 13f
                isAllCaps = false
                setPadding(0, 0, 0, 0)
                setTextColor(textColor)
                setBackgroundColor(cardColor)
                layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                setOnClickListener { showTab(index) }
            }
        }
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(6))
        }
        tabButtons.forEach { tabRow.addView(it) }
        root.addView(tabRow)

        // ===== 小分類（サブタブ）の器 =====
        subTabArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), dp(8))
        }
        root.addView(subTabArea)

        // ===== コンテンツ領域 =====
        contentArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(contentArea)

        setContentView(root)
        showTab(0)
    }

    /** 各大分類タブの小分類名 */
    private fun subTabNames(tab: Int): List<String> = when (tab) {
        0 -> listOf("端末情報", "バッテリー", "フォルダ集計")
        1 -> listOf("通信ログ", "SaaS接続", "ルーター")
        2 -> listOf("アプリ棚卸し", "端末診断", "緊急対応",
            "APK掃除", "権限監査", "その他")
        else -> emptyList()
    }

    private fun showTab(index: Int) {
        currentTab = index
        tabButtons.forEachIndexed { i, b ->
            b.setBackgroundColor(if (i == index) accentColor else cardColor)
            b.setTextColor(if (i == index) Color.BLACK else textColor)
        }
        buildSubTabs(index)
        renderContent()
    }

    private fun showSub(sub: Int) {
        when (currentTab) {
            0 -> statusSub = sub
            1 -> commsSub = sub
            2 -> diagSub = sub
        }
        buildSubTabs(currentTab)
        renderContent()
    }

    private fun currentSub(): Int = when (currentTab) {
        0 -> statusSub
        1 -> commsSub
        2 -> diagSub
        else -> 0
    }

    private fun buildSubTabs(tab: Int) {
        subTabArea.removeAllViews()
        val names = subTabNames(tab)
        if (names.isEmpty()) {
            subTabArea.visibility = View.GONE
            return
        }
        subTabArea.visibility = View.VISIBLE
        val cur = currentSub()
        // 1行あたり最大3つ。多い場合は複数行に折り返す
        val perRow = if (names.size <= 3) names.size else 3
        val rows = (names.size + perRow - 1) / perRow
        for (r in 0 until rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (r > 0) topMargin = dp(4) }
            }
            for (col in 0 until perRow) {
                val i = r * perRow + col
                if (i >= names.size) {
                    // 幅を揃えるためのダミー
                    row.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f)
                    })
                    continue
                }
                val sel = cur == i
                row.addView(Button(this).apply {
                    text = names[i]
                    textSize = 11f
                    isAllCaps = false
                    setPadding(0, 0, 0, 0)
                    setTextColor(if (sel) Color.BLACK else subColor)
                    setBackgroundColor(if (sel) greenColor else cardColor)
                    layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                        setMargins(dp(2), 0, dp(2), 0)
                    }
                    setOnClickListener { showSub(i) }
                })
            }
            subTabArea.addView(row)
        }
    }

    private fun renderContent() {
        contentArea.removeAllViews()
        val v = when (currentTab) {
            0 -> when (statusSub) {
                1 -> buildBatteryView()
                2 -> buildDigestView()
                else -> buildStatusView()
            }
            1 -> when (commsSub) {
                1 -> buildToolLauncher(1, "SaaS接続確認",
                    "契約中のSaaSのログイン先を登録し、公衆Wi-Fiで別サイトへの" +
                    "リダイレクトや証明書のすり替えが起きていないか照合します。")
                2 -> buildToolLauncher(2, "Wi-Fi／ルーター診断",
                    "接続中のWi-Fiの暗号方式、ルーターの開放ポート、DNSの向き先を" +
                    "確認して総合評価します。自宅の回線で実行してください。")
                else -> buildCommsView()
            }
            2 -> when (diagSub) {
                1 -> buildPostureView()
                2 -> buildEmergencyView()
                3 -> buildApkCleanView()
                4 -> buildA11yAuditView()
                5 -> buildOtherView()
                else -> buildInventoryView()
            }
            3 -> buildPcView()
            else -> buildManualView()
        }
        contentArea.addView(v)
    }

    // =========================================================
    // タブ1: アプリ棚卸し
    // =========================================================
    private fun buildInventoryView(): View {
        val apps = cachedApps ?: scanApps().also { cachedApps = it }

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }

        // サマリー
        val cnt = intArrayOf(0, 0, 0)
        apps.forEach { cnt[it.classification]++ }
        list.addView(card().apply {
            addView(row("許可済み", "${cnt[0]} 件", greenColor))
            addView(row("要監視", "${cnt[1]} 件", yellowColor))
            addView(row("要対策", "${cnt[2]} 件", redColor))
        })

        // 要対策 → 要監視 → 許可済み の順で表示
        apps.sortedByDescending { it.classification * 100 + it.score }.forEach { app ->
            val badgeColor = when (app.classification) {
                2 -> redColor; 1 -> yellowColor; else -> greenColor
            }
            val badgeText = when (app.classification) {
                2 -> "要対策"; 1 -> "要監視"; else -> "許可済み"
            }
            list.addView(card().apply {
                addView(TextView(this@MainActivity).apply {
                    text = app.label
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(textColor)
                })
                addView(TextView(this@MainActivity).apply {
                    text = "入手元: ${app.installer}　リスク: ${app.score}点"
                    textSize = 12f
                    setTextColor(subColor)
                    setPadding(0, dp(2), 0, dp(2))
                })
                addView(TextView(this@MainActivity).apply {
                    text = "● $badgeText"
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(badgeColor)
                })
                setOnClickListener { showAppDetail(app) }
            })
        }

        return ScrollView(this).apply { addView(list) }
    }

    private fun scanApps(): List<AppRisk> {
        val pm = packageManager
        val packages: List<PackageInfo> = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val result = mutableListOf<AppRisk>()

        for (pkg in packages) {
            val ai = pkg.applicationInfo ?: continue
            // ユーザーインストールアプリのみ（システムアプリ除外）
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !isUpdatedSystem) continue

            val installerPkg: String? = try {
                if (Build.VERSION.SDK_INT >= 30) {
                    pm.getInstallSourceInfo(pkg.packageName).installingPackageName
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstallerPackageName(pkg.packageName)
                }
            } catch (e: Exception) { null }

            val installer: String
            val sideloaded: Boolean
            when (installerPkg) {
                "com.android.vending" -> { installer = "Play ストア"; sideloaded = false }
                "com.amazon.venezia" -> { installer = "Amazon"; sideloaded = false }
                null -> { installer = "不明（手動/ADB）"; sideloaded = true }
                else -> { installer = installerPkg; sideloaded = true }
            }

            // 実際に付与されている危険権限を集計
            val granted = mutableListOf<String>()
            val reqs = pkg.requestedPermissions
            val flags = pkg.requestedPermissionsFlags
            if (reqs != null && flags != null) {
                for (i in reqs.indices) {
                    val name = reqs[i]
                    val isGranted = (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    if (isGranted && dangerousPerms.containsKey(name)) {
                        granted.add(dangerousPerms[name] ?: name)
                    }
                }
            }

            var score = granted.size
            if (sideloaded) score += 3

            val classification = when {
                sideloaded || score >= 6 -> 2
                score >= 3 -> 1
                else -> 0
            }

            result.add(
                AppRisk(
                    label = ai.loadLabel(pm).toString(),
                    packageName = pkg.packageName,
                    installer = installer,
                    isSideloaded = sideloaded,
                    grantedDangerous = granted,
                    score = score,
                    classification = classification
                )
            )
        }
        return result
    }

    private fun showAppDetail(app: AppRisk) {
        val sb = StringBuilder()
        sb.append("パッケージ:\n${app.packageName}\n\n")
        sb.append("入手元: ${app.installer}\n")
        sb.append("リスクスコア: ${app.score}点\n")
        if (app.isSideloaded) sb.append("※ ストア外入手のため +3点\n")
        sb.append("\n付与済みの危険権限 (${app.grantedDangerous.size}件):\n")
        if (app.grantedDangerous.isEmpty()) {
            sb.append("なし")
        } else {
            app.grantedDangerous.forEach { sb.append("・$it\n") }
        }
        sb.append("\n【守りのDX 2.0の考え方】\n")
        sb.append(when (app.classification) {
            2 -> "即削除ではなく、まず入手元と権限の妥当性を確認。業務に必要なら権限を最小化して使い続ける判断も。"
            1 -> "権限がやや多めです。設定→アプリ→権限で不要なものをOFFにすれば許可済みに近づけられます。"
            else -> "現時点で問題ありません。このまま利用を継続できます。"
        })

        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setMessage(sb.toString())
            .setPositiveButton("閉じる", null)
            .show()
    }

    // =========================================================
    // タブ2: 端末診断
    // =========================================================
    private fun buildPostureView(): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }

        data class Check(val name: String, val ok: Boolean, val detail: String)
        val checks = mutableListOf<Check>()

        // 画面ロック
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val lockOk = km.isDeviceSecure
        checks.add(Check("画面ロック", lockOk,
            if (lockOk) "PIN/パターン/生体認証が設定済み"
            else "未設定。紛失時に情報漏洩リスク大。今すぐ設定を"))

        // USBデバッグ
        val adbOn = Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        checks.add(Check("USBデバッグ", !adbOn,
            if (adbOn) "ON。開発時以外はOFF推奨（PC接続時のデータ抜き取りリスク）"
            else "OFF。問題ありません"))

        // 開発者オプション
        val devOn = Settings.Global.getInt(contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        checks.add(Check("開発者オプション", !devOn,
            if (devOn) "ON。開発用途でなければOFF推奨"
            else "OFF。問題ありません"))

        // セキュリティパッチ
        val patch = Build.VERSION.SECURITY_PATCH
        var patchOk = false
        var patchDetail = "パッチ日: $patch"
        try {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(patch)
            if (d != null) {
                val ageDays = (Date().time - d.time) / (1000L * 60 * 60 * 24)
                patchOk = ageDays <= 180
                patchDetail = if (patchOk) "パッチ日: $patch（${ageDays}日前・良好）"
                else "パッチ日: $patch（${ageDays}日前）。半年以上前です。OSアップデートを確認してください"
            }
        } catch (e: Exception) { }
        checks.add(Check("セキュリティパッチ", patchOk, patchDetail))

        // OSバージョン
        val osOk = Build.VERSION.SDK_INT >= 31
        checks.add(Check("OSバージョン", osOk,
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" +
                if (osOk) "" else "。古いOSは脆弱性修正が届かない場合があります"))

        // ストア外アプリ
        val sideCount = (cachedApps ?: scanApps().also { cachedApps = it })
            .count { it.isSideloaded }
        checks.add(Check("ストア外アプリ", sideCount == 0,
            if (sideCount == 0) "ストア外から入手したアプリはありません"
            else "$sideCount 件のストア外アプリを検出。棚卸しタブで確認してください"))

        // 総合判定
        val okCount = checks.count { it.ok }
        val grade = when {
            okCount == checks.size -> "A（良好）" to greenColor
            okCount >= checks.size - 2 -> "B（要改善）" to yellowColor
            else -> "C（要対策）" to redColor
        }
        list.addView(card().apply {
            addView(TextView(this@MainActivity).apply {
                text = "総合判定: ${grade.first}"
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(grade.second)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = "${okCount} / ${checks.size} 項目クリア"
                textSize = 13f
                setTextColor(subColor)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        })

        checks.forEach { c ->
            list.addView(card().apply {
                addView(TextView(this@MainActivity).apply {
                    text = (if (c.ok) "○ " else "× ") + c.name
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (c.ok) greenColor else redColor)
                })
                addView(TextView(this@MainActivity).apply {
                    text = c.detail
                    textSize = 13f
                    setTextColor(textColor)
                    setPadding(0, dp(4), 0, 0)
                })
            })
        }

        return ScrollView(this).apply { addView(list) }
    }

    // =========================================================
    // タブ4: 説明書（機能ごとにページを分割）
    // =========================================================
    private var manualPage = 0

    private fun buildManualView(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 説明書内のページ切替（機能ごと）
        val pageNames = listOf("概要", "状態", "電池・集計", "通信",
            "棚卸し", "端末診断", "緊急", "PC", "その他")
        val pageBtns = pageNames.mapIndexed { i, name ->
            Button(this).apply {
                text = name
                textSize = 10f
                isAllCaps = false
                setPadding(0, 0, 0, 0)
                val sel = manualPage == i
                setTextColor(if (sel) Color.BLACK else subColor)
                setBackgroundColor(if (sel) accentColor else cardColor)
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    manualPage = i
                    renderContent()
                }
            }
        }
        val selRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), 0, dp(10), dp(4))
        }
        val selRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), 0, dp(10), dp(8))
        }
        pageBtns.forEachIndexed { i, b ->
            if (i < 5) selRow1.addView(b) else selRow2.addView(b)
        }
        // 2段目は4つなので、幅を1段目と揃えるためのダミー
        selRow2.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
        })
        outer.addView(selRow1)
        outer.addView(selRow2)

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }

        fun section(title: String, body: String) {
            list.addView(card().apply {
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(accentColor)
                })
                addView(TextView(this@MainActivity).apply {
                    text = body
                    textSize = 13f
                    setTextColor(textColor)
                    setLineSpacing(dp(2).toFloat(), 1f)
                    setPadding(0, dp(6), 0, 0)
                })
            })
        }

        when (manualPage) {
            // ---------- 概要 ----------
            0 -> {
                section("守りのDX 2.0 とは",
                    "従来のセキュリティ対策は「会社が許可していないアプリ（シャドーIT）を見つけたら即削除」でした。" +
                    "しかし現場が便利なSaaSを使うのには理由があります。\n\n" +
                    "守りのDX 2.0は発想を変えます。\n" +
                    "①まず可視化する\n" +
                    "②有益なものは使い続けられるようにする\n" +
                    "③その代わり漏洩しない仕組み（ガードレール）を置く\n\n" +
                    "禁止ではなく、安全に使うための土台づくりです。")

                section("画面の構成（5つのタブ）",
                    "機能は次の5つに分類しています。各タブの上にある緑のボタンで、" +
                    "さらに細かい機能を切り替えられます。\n\n" +
                    "①状態 … 今のスマホの状況を見る\n" +
                    "　端末情報／バッテリー／フォルダ集計\n\n" +
                    "②通信 … Wi-Fiや通信に関すること\n" +
                    "　通信ログ／SaaS接続／ルーター診断\n\n" +
                    "③診断 … 調べる・緊急時に対応する\n" +
                    "　アプリ棚卸し／端末診断／緊急対応／その他\n\n" +
                    "④PC … パソコンの点検\n\n" +
                    "⑤説明書 … この画面")

                section("はじめての方の進め方",
                    "手順1. 「状態」で今の端末の様子をひと通り見る\n" +
                    "手順2. 「診断」→端末診断で土台の防御力を確認\n" +
                    "手順3. 「診断」→アプリ棚卸しで入っているアプリを3分類\n" +
                    "手順4. 「通信」→ルーター診断で自宅のWi-Fiを点検\n" +
                    "手順5. 「PC」で業務パソコンの対策状況を点検\n" +
                    "手順6. 日常の共有時は「関所」を通す（診断→その他に説明あり）\n\n" +
                    "怪しいリンクを踏んだときは、すぐ「診断」→緊急対応へ。")

                section("このアプリの通信について",
                    "診断結果・DNSログ・ポリシー・台帳は、すべて端末内にのみ保存され、" +
                    "当アプリの開発者や第三者へ送信されることは一切ありません。\n\n" +
                    "外部と通信するのは次の2機能だけで、接続先は利用者が登録したURLに限られます。\n" +
                    "・ツール→SaaS接続確認（登録したSaaSへ接続して照合）\n" +
                    "・ツール→機器台帳の更新確認（登録したメーカーページを取得）\n\n" +
                    "通信ログ機能のVPNは端末内で完結し、外部のVPNサーバーには接続しません。")

                section("Appathy",
                    "Less Motivation, More Automation.\n" +
                    "本アプリはスマホのみ（Termux + GitHub Actions）で開発されています。")
            }
            // ---------- 状態 ----------
            1 -> {
                section("状態タブ｜端末情報",
                    "状態タブの「端末情報」画面の説明です。" +
                    "バッテリーとフォルダ集計は次のページで説明します。\n\n" +
                    "端末の現在の状況をひと目で確認する画面です。" +
                    "普段の値を知っておくと、「いつもと違う」ことに気付けるようになります。\n\n" +
                    "画面を開くたびに自動で取得します。" +
                    "手動で取り直したいときは上の「最新の状態に更新」を押してください。")

                section("表示される6項目の見方",
                    "①ストレージ … 全体の使用量と使用率をバーで表示。" +
                    "90%を超えると赤くなります。空きが少ないとOSの更新を適用できなくなるため、" +
                    "セキュリティ上も重要な項目です。\n\n" +
                    "②Androidバージョン … OS名・機種・ビルド・セキュリティパッチ日を表示。" +
                    "パッチが半年以上前、またはOSが古い場合は黄色で警告します。\n\n" +
                    "③接続中のWi-Fi … 接続先の名前、暗号方式、リンク速度、IPアドレス。" +
                    "外出先で意図しないWi-Fiに繋がっていないかの確認に使えます。\n\n" +
                    "④Bluetooth … 接続中の機器とペアリング済み機器の一覧。" +
                    "身に覚えのない機器が登録されていないか確認できます。\n\n" +
                    "⑤接続中の外部デバイス … USB機器やUSBメモリ・SDカード。" +
                    "種別（キーボード、ストレージ等）とメーカー、容量を表示します。\n\n" +
                    "⑥起動中のアプリ … 現在動作しているアプリ。" +
                    "緑色は画面に表示中、それ以外は背後で動作中です。")

                section("この画面での気付き方",
                    "・Wi-Fi名が意図しない名前になっていないか（偽アクセスポイントの疑い）\n" +
                    "・Bluetoothに見覚えのない機器がペアリングされていないか\n" +
                    "・身に覚えのないUSB機器が接続されていないか\n" +
                    "・セキュリティパッチが古すぎないか\n" +
                    "・ストレージが逼迫して更新が止まっていないか\n\n" +
                    "怪しい点が見つかった場合は「緊急対応」タブの侵害チェックもあわせて実行してください。")

                section("表示できる範囲の限界（正直な説明）",
                    "【起動中のアプリ】Android 5.0以降のOS仕様により、" +
                    "他のアプリの動作状況はほとんど取得できません。" +
                    "ここに表示されるのは一部で、多くの端末では本アプリ自身しか出ません。" +
                    "全体を確認するには、端末の「最近使ったアプリ」画面や" +
                    "設定→アプリ→実行中のサービスをご覧ください。" +
                    "画面下のボタンから設定画面を開けます。\n\n" +
                    "【Wi-Fi名】Androidの仕様上、位置情報の許可がないと取得できません。" +
                    "許可ボタンから設定できます。\n\n" +
                    "【Bluetooth】Android 12以降は「近くのデバイス」の許可が必要です。" +
                    "また、イヤホン等はOSの仕様で接続中と判定されない場合があるため、" +
                    "ペアリング済み一覧もあわせてご確認ください。\n\n" +
                    "【アップデートの確認】アプリから更新の有無を直接調べることはOSの仕様上できません。" +
                    "本アプリはバージョンとパッチ日から古さを判定し、" +
                    "実際の確認は端末の「システム更新」画面へ案内する方式にしています。\n\n" +
                    "【ストレージ】表示は端末全体の使用量です。" +
                    "アプリごとの内訳は端末の設定→ストレージで確認できます。")
            }
            // ---------- 電池・集計 ----------
            2 -> {
                section("バッテリーの劣化度｜これは何？",
                    "バッテリーが新品時と比べてどれだけ劣化しているかを推定表示します。" +
                    "劣化したバッテリーは急な電源断を招き、業務端末としての信頼性を下げます。")

                section("使い方手順",
                    "手順1. 状態タブ →「バッテリー」を押す\n" +
                    "手順2. 表示された劣化度と評価を確認\n" +
                    "手順3. 値を安定させたい場合は、充電器を外し残量50〜80%程度で「再測定」")

                section("見方",
                    "・劣化◯% … 新品時からどれだけ容量が減ったかの推定値\n" +
                    "・健康度◯% … 100%が新品相当。80%以上なら通常の使用範囲\n" +
                    "・バーの色 … 緑=良好、黄=交換検討、赤=劣化大\n\n" +
                    "目安:\n" +
                    "・90%以上 … 良好\n" +
                    "・80〜89% … 軽度の劣化。まだ交換不要\n" +
                    "・70〜79% … 外出時に電池切れが起きやすい。交換検討\n" +
                    "・70%未満 … 急な電源断の恐れ。交換推奨\n\n" +
                    "あわせて残量・充電状態・温度・電圧・OS報告のバッテリー状態も表示します。" +
                    "温度が45℃を超えている場合は警告が出ます。")

                section("この機能の限界（重要）",
                    "Androidには劣化度を返す公開APIがありません。" +
                    "本アプリは端末内部の設計容量と充電カウンタから推定しています。\n\n" +
                    "そのため、機種によっては「算出できませんでした」と表示されます。" +
                    "これは異常ではなく、その端末が必要な値を公開していないためです。" +
                    "その場合はOS報告の状態・温度・電圧を参考にし、" +
                    "端末の設定→バッテリーもあわせてご確認ください。\n\n" +
                    "算出できた場合も推定値であり、誤差があります。" +
                    "残量が極端に少ない時や充電直後は特に誤差が大きくなります。" +
                    "正確な数値が必要な場合はメーカーの点検を受けてください。")

                section("フォルダ集計（ダイジェスト）｜これは何？",
                    "選んだフォルダの中身を1画面で集計し、日付つきで記録します。" +
                    "前回との差を見ることで「知らないうちにファイルが増えた」" +
                    "「見覚えのない実行ファイルが入った」ことに気付けます。")

                section("使い方手順",
                    "手順1. 状態タブ →「フォルダ集計」を押す\n" +
                    "手順2. 「フォルダを選んで集計」を押す\n" +
                    "手順3. 対象フォルダ（ダウンロード等）を選んで「このフォルダを使用」\n" +
                    "手順4. 集計結果を確認\n" +
                    "手順5. 数日後、同じフォルダで「再集計」を押すと前回との差が出ます")

                section("見方",
                    "・大きな数字 … ファイル総数\n" +
                    "・種類別の内訳 … 文書／画像／動画／音声／圧縮／その他／拡張子なし／危険\n" +
                    "　「危険」は赤字で表示されます\n" +
                    "・前回からの変化 … 増減数と、危険な拡張子の増加\n" +
                    "・注意が必要なファイル … 該当ファイル名と理由の一覧\n\n" +
                    "危険と判定する拡張子: 実行ファイル(.exe .scr等)、スクリプト(.bat .js .vbs等)、" +
                    "インストーラ、ショートカット(.lnk)、マクロ付きOffice(.docm .xlsm等)、" +
                    "Androidアプリ(.apk)。加えて二重拡張子（請求書.pdf.exe）も検出します。\n\n" +
                    "同じフォルダを再集計すると記録が追記され、最大30回分の履歴が残ります。")

                section("この機能の限界",
                    "拡張子と名前による判定のみで、ウイルス定義は持ちません。" +
                    "「危険0件＝安全」ではありません。\n\n" +
                    "ファイル数が非常に多いフォルダは途中で打ち切られます（その旨が表示されます）。" +
                    "端末全体ではなく、ダウンロードフォルダなど" +
                    "変化を見たい場所を絞って使うのが効果的です。")
            }
            // ---------- 通信 ----------
            3 -> {
                section("通信ログタブ｜これは何？",
                    "端末内VPNの仕組みで、アプリが「どのドメイン（SaaS等）へ通信しようとしたか」を" +
                    "DNSレベルで記録する機能です。会社が把握していないSaaS利用（シャドーIT）の発見に使います。")

                section("使い方手順",
                    "手順1. 画面上部の「通信ログ」タブを押す\n" +
                    "手順2. 「記録を開始」ボタンを押す\n" +
                    "手順3. 初回はAndroidの「接続リクエスト（VPN）」ダイアログが出るのでOKを押す\n" +
                    "手順4. 通知バーに「守りのDX：通信記録中」が表示されたことを確認\n" +
                    "手順5. 調べたいアプリ（ブラウザやSaaSアプリ等）をいつも通り操作する\n" +
                    "手順6. このタブに戻ると、通信先ドメインが回数順に一覧表示される\n" +
                    "手順7. 各ドメインの「許可／記録のみ／ブロック」ボタンで仕分けする\n" +
                    "手順8. 棚卸しが済んだら必ず「記録を停止」を押す\n" +
                    "手順9. やり直したい時は「ログを消去」")

                section("見方",
                    "ドメインごとのカード表示:\n" +
                    "・1行目=通信先ドメイン（例: example.com）\n" +
                    "・2行目=問い合わせ回数（多い=そのサービスをよく使っている）\n" +
                    "・3行目=ポリシーボタン（現在の設定が色付きで表示）\n\n" +
                    "ポリシーの考え方:\n" +
                    "・許可（緑）=業務に有益と判断したSaaS。使い続けてOK\n" +
                    "・記録のみ（青）=様子見。既定値\n" +
                    "・ブロック（赤）=使わせたくない通信先")

                section("必ず知っておくこと（重要）",
                    "①現バージョンは『記録専用モード』です。記録中は端末の通信が実際には流れません。" +
                    "常時ONではなく、棚卸ししたい時だけONにしてください。\n\n" +
                    "②「ブロック」は現在ポリシーの記録のみで、実際の遮断は行いません（Phase 2.5で対応予定）。\n\n" +
                    "③記録されるのはドメイン名のみで、通信の中身（本文やパスワード）は一切見ません。" +
                    "ログは端末内保存・外部送信なし・最大500件です。")
            }
            // ---------- 棚卸し ----------
            4 -> {
                section("アプリ棚卸しタブ｜これは何？",
                    "端末にインストールされているアプリを自動でスキャンし、" +
                    "リスクの高さで「許可済み／要監視／要対策」の3つに分類する機能です。" +
                    "シャドーIT可視化の第一歩になります。")

                section("使い方手順",
                    "手順1. 画面上部の「アプリ棚卸し」タブを押す\n" +
                    "手順2. 自動でスキャンが始まり、一覧が表示される（操作不要）\n" +
                    "手順3. 一番上のサマリーで3分類の件数を確認\n" +
                    "手順4. 気になるアプリをタップすると詳細画面が開く\n" +
                    "手順5. 詳細の「守りのDX 2.0の考え方」に沿って対処する\n" +
                    "手順6. 権限を減らしたい場合は、Androidの設定→アプリ→対象アプリ→権限 でOFFにする\n" +
                    "手順7. 権限変更後にこのタブへ戻ると、分類が更新される")

                section("見方",
                    "● 許可済み（緑）: ストア入手で権限も最小限。そのまま利用OK\n" +
                    "● 要監視（黄）: 危険権限が多め。権限の見直しで緑にできます\n" +
                    "● 要対策（赤）: ストア外入手、または権限が非常に多い。まず入手元の確認を\n\n" +
                    "各行の表示:\n" +
                    "・1行目=アプリ名\n" +
                    "・2行目=入手元（Playストア/不明など）とリスク点数\n" +
                    "・3行目=分類バッジ\n\n" +
                    "点数の計算式: 付与済みの危険権限1つ=1点、ストア外入手=+3点。" +
                    "0〜2点=許可済み、3〜5点=要監視、6点以上またはストア外=要対策。")

                section("よくある質問",
                    "Q. 赤＝削除すべき？\n" +
                    "A. いいえ。守りのDX 2.0では即削除しません。業務に必要なら権限を最小化して使い続ける判断もあります。\n\n" +
                    "Q. 一覧に出ないアプリがある\n" +
                    "A. Android標準のシステムアプリは対象外です（ユーザーが入れたアプリのみ表示）。")
            }
            // ---------- 端末診断 ----------
            5 -> {
                section("端末診断タブ｜これは何？",
                    "アプリ単位ではなく、端末そのものの防御力を6項目でチェックし、" +
                    "A/B/Cの総合判定を出す機能です。土台が弱いと、どんな対策も効果が半減します。")

                section("使い方手順",
                    "手順1. 画面上部の「端末診断」タブを押す\n" +
                    "手順2. 自動で6項目のチェックが実行される（操作不要）\n" +
                    "手順3. 一番上の総合判定（A/B/C）を確認\n" +
                    "手順4. ×が付いた項目のカードを読む（対処方法が書いてあります）\n" +
                    "手順5. Androidの設定画面で該当項目を修正する\n" +
                    "手順6. このタブに戻り直すと再チェックされ、判定が更新される")

                section("見方",
                    "総合判定:\n" +
                    "・A（良好）=全項目クリア\n" +
                    "・B（要改善）=1〜2項目が未達\n" +
                    "・C（要対策）=3項目以上が未達\n\n" +
                    "6つのチェック項目:\n" +
                    "①画面ロック: 紛失時の情報漏洩を防ぐ最重要項目\n" +
                    "②USBデバッグ: ONだとPC接続でデータを抜かれる恐れ\n" +
                    "③開発者オプション: 開発用途でなければOFF推奨\n" +
                    "④セキュリティパッチ: 半年以上前なら×\n" +
                    "⑤OSバージョン: 古いOSは脆弱性修正が届かないことがある\n" +
                    "⑥ストア外アプリ: 棚卸しタブと連動した件数表示\n\n" +
                    "優先順位: まず①画面ロック、次に④パッチ更新を対応してください。")
            }
            // ---------- 緊急 ----------
            6 -> {
                section("緊急対応タブ｜これは何？",
                    "怪しいリンクを踏んでしまった／踏みそうな時のための機能です。" +
                    "「開く前の検査」「踏んだ後の侵害チェック」「応急処置の手順」の3つが入っています。")

                section("① リンク検査の使い方手順",
                    "手順1. 怪しいリンクを長押しして「リンクをコピー」を選ぶ（開かないこと）\n" +
                    "手順2. 緊急対応タブの入力欄に長押しで貼り付ける\n" +
                    "手順3. 「このリンクを検査」を押す\n" +
                    "手順4. 結果画面で判定と理由を確認する\n\n" +
                    "共有メニューから『守りのDX関所』を選んでも、リンクは自動で検査されます。")

                section("① リンク検査の見方",
                    "判定は3段階です。\n" +
                    "・危険 = 開かないでください\n" +
                    "・注意 = 理由を読んで慎重に判断\n" +
                    "・明らかな危険信号なし\n\n" +
                    "「実際の接続先」の表示が最重要です。文面がどんなに本物らしくても、" +
                    "ここが公式ドメインでなければ偽サイトです。\n\n" +
                    "主な検出項目: 有名企業名を含む偽ドメイン、@を使った偽装、" +
                    "Punycode（そっくり文字）、IPアドレス直打ち、短縮URL、" +
                    "危険度の高いドメイン種別、APKの直接ダウンロード、暗号化なし(http)。\n\n" +
                    "【重要】判定は端末内のパターン照合のみで行うため、" +
                    "『危険信号なし＝安全』ではありません。新種の詐欺サイトや、" +
                    "正規サイトが乗っ取られている場合は検出できません。" +
                    "ログインやカード入力は、リンクからではなく公式アプリやブックマークから行ってください。")

                section("② 侵害チェックの使い方手順",
                    "手順1. 「直近24時間」か「直近7日間」を選ぶ（踏んだ時期に合わせる）\n" +
                    "手順2. 「侵害チェックを実行」を押す\n" +
                    "手順3. 検出された項目を上から順に確認する（赤が最優先）\n" +
                    "手順4. 各カードのボタンから、削除や設定画面へ直接移動できる\n" +
                    "手順5. 対処後にもう一度実行して、消えたことを確認する")

                section("② 侵害チェックの見方",
                    "確認する6分類:\n" +
                    "・最近入ったアプリ = 踏んだ後に勝手に入っていないか\n" +
                    "・ユーザー補助が有効 = 画面の盗み見と自動操作ができる最危険権限\n" +
                    "・通知の読み取りが有効 = SMSの認証コードを盗まれる恐れ\n" +
                    "・端末管理者アプリ = 削除を妨害される。身に覚えがなければ即無効化\n" +
                    "・アプリを追加インストールできる = 再侵入の入口\n" +
                    "・既定のSMSアプリ／ブラウザ = 乗っ取られるとリンクや認証コードを横取りされる\n\n" +
                    "色の意味: 赤=至急対応、黄=確認推奨、灰=標準アプリのため通常は問題なし。")

                section("③ 応急処置チェックリスト",
                    "踏んだ直後は手順1から順に対応してください。" +
                    "手順1〜4と手順9はボタンから設定画面へ直接移動できます。\n\n" +
                    "特に重要な3点:\n" +
                    "・偽サイトにID/パスワードを入力した場合は、必ずパスワード変更（別の安全な端末から）\n" +
                    "・使い回している他サービスのパスワードもすべて変更\n" +
                    "・会社の端末なら隠さず即報告。『日時・URL・入力の有無・入れたアプリ』を伝える")

                section("できないこと（正直な限界）",
                    "このアプリは、既に盗まれた情報を取り戻すことはできません。" +
                    "また、端末の奥深くに潜む高度なマルウェアの検出や駆除もできません。" +
                    "検出項目に該当がなくても症状（勝手な操作、身に覚えのない通知や請求）が続く場合は、" +
                    "データのバックアップ後に端末の初期化を検討し、" +
                    "会社端末なら情報システム部門へ相談してください。")
            }
            // ---------- PC ----------
            7 -> {
                section("パソコンタブ｜これは何？",
                    "パソコンは使い方によって必要な対策が変わります。" +
                    "利用目的を選ぶと、その用途で本当に必要な項目だけのチェックリストが出て、" +
                    "未対応のものを優先順位つきで提言します。\n\n" +
                    "スマホから点検できるようにしてあるので、" +
                    "パソコンの前に座って画面を見ながら確認する使い方を想定しています。")

                section("使い方手順",
                    "手順1. OS（Windows / Mac）を選ぶ。提言の操作手順がOSに合わせて変わります\n" +
                    "手順2. 利用目的を選ぶ（複数選択可）\n" +
                    "　・屋外利用（持ち出す）\n" +
                    "　・機密情報を保存する\n" +
                    "　・複数人でシェアする\n" +
                    "手順3. 表示されたチェックリストのうち、できている項目にチェック\n" +
                    "手順4. 一番下の「評価する」を押す\n" +
                    "手順5. 画面上部に評価と提言が表示される\n" +
                    "手順6. 提言の「最優先で対応」から順に対処する\n" +
                    "手順7. 対処できたらチェックを入れ、もう一度評価する\n\n" +
                    "チェック内容とOS・目的の選択は自動保存されるので、" +
                    "途中でアプリを閉じても続きから再開できます。")

                section("チェック項目の構成",
                    "・共通（土台）… どの用途でも必要な10項目。更新、ウイルス対策、" +
                    "アカウント分離、バックアップ、マクロ無効化など\n" +
                    "・屋外利用 … ディスク暗号化、復帰時パスワード、公衆Wi-Fi対策、" +
                    "遠隔ロック、のぞき見・置き忘れ対策など\n" +
                    "・機密情報を保存 … 保管場所の把握、ファイル暗号化、権限制限、" +
                    "同期フォルダの分離、廃棄手順、私物端末の禁止など\n" +
                    "・複数人でシェア … 個別アカウント、管理者の限定、データ分離、" +
                    "ログ確認、退職時手順など\n\n" +
                    "目的を複数選ぶと、その分だけ項目が増えます。")

                section("評価の見方",
                    "各項目には重要度がついています。\n" +
                    "・[必須]（赤）= これが欠けると他の対策が意味を失う項目\n" +
                    "・[重要]（黄）= その用途で通常求められる水準\n" +
                    "・[推奨]（灰）= 余裕があれば取り組む項目\n\n" +
                    "達成度は単純な個数ではなく、重要度で重みづけして計算します。\n\n" +
                    "評価:\n" +
                    "・A（良好）= 必須と重要をすべて満たしている（推奨の残りは可）\n" +
                    "・B（要改善）= 必須は満たすが重要項目に穴がある\n" +
                    "・C（要対策）= 必須項目に穴がある\n" +
                    "・D（危険）= 必須項目が3件以上未対応\n\n" +
                    "必須項目が1つでも欠けているとA評価にはなりません。" +
                    "個数の多さより、必須を埋めることを優先してください。")

                section("提言の使い方",
                    "提言は「最優先で対応（必須）」「次に対応（重要）」「余裕があれば（推奨）」の" +
                    "3段階で表示されます。各項目には" +
                    "『なぜ必要か』と『具体的な対処手順』が併記されます。\n\n" +
                    "対処手順は選んだOSに合わせた設定画面の場所まで書いてあるので、" +
                    "そのままパソコンを操作できます。")

                section("この機能の限界",
                    "この評価は自己申告に基づく目安です。" +
                    "スマホからパソコンの実際の設定値を読み取ることはできないため、" +
                    "「チェックを入れたつもりだが実際は無効だった」場合は検出できません。\n\n" +
                    "重要な判断に使う場合は、提言に書かれた設定画面を実際に開いて" +
                    "現在の値を確認してからチェックを入れてください。" +
                    "また、社内規程や業界基準がある場合は、そちらとの整合も別途確認が必要です。")
            }
            // ---------- その他 ----------
            8 -> {
                section("APK掃除｜これは何？",
                    "端末に残ったAPK（アプリのインストール用ファイル）を探して一括削除する機能です。\n\n" +
                    "APKが残っていると、誤って実行して不正アプリを入れてしまう危険があります。" +
                    "また古いAPKは既知の弱点を含んだままのことが多く、置いておく利点はほぼありません。")

                section("APK掃除の使い方手順",
                    "手順1. 診断タブ →「APK掃除」を押す\n" +
                    "手順2. 「フォルダを選んでAPKを探す」を押す\n" +
                    "手順3. ダウンロードフォルダなど、対象を選んで「このフォルダを使用」\n" +
                    "　（端末全体を対象にしたい場合は内部ストレージの最上位を選ぶ）\n" +
                    "手順4. 見つかった一覧とファイル名・サイズ・日付を確認\n" +
                    "手順5. 問題なければ「すべて削除」→確認画面で「削除する」\n" +
                    "手順6. 削除後は自動で再検索され、結果が更新されます\n\n" +
                    "削除は取り消せません。必要なAPKがある場合は先に別の場所へ移してください。")

                section("提供元不明アプリの許可について",
                    "同じ画面の下半分に、「ストアを経由せずアプリを導入できる」許可が" +
                    "付いているアプリの一覧が出ます。これは不正アプリの主要な侵入口です。\n\n" +
                    "見方:\n" +
                    "・赤 … 心当たりがなければ無効化を推奨\n" +
                    "・黄 … ブラウザやファイル管理アプリ。自分でアプリを入れないなら無効化\n\n" +
                    "【重要】Androidの仕様上、この設定をアプリから直接変更することはできません。" +
                    "「インストール許可の設定を開く」または各アプリの「設定を開く」ボタンから、" +
                    "端末の設定画面で切り替えてください。")

                section("権限監査（ユーザー補助）｜これは何？",
                    "ユーザー補助（アクセシビリティ）権限を持つアプリの一覧と、" +
                    "そのリスク評価を表示します。\n\n" +
                    "この権限は「画面に表示されている内容をすべて読み取る」" +
                    "「利用者の代わりに操作する」ことができる最も強力なもので、" +
                    "不正アプリが真っ先に狙う設定です。" +
                    "入力中のパスワードの盗み見や、勝手な送金操作も技術的に可能になります。")

                section("権限監査の使い方手順",
                    "手順1. 診断タブ →「権限監査」を押す（自動で監査が実行されます）\n" +
                    "手順2. 上部の判定と、有効になっているアプリの一覧を確認\n" +
                    "手順3. 各カードの「できること」「判定理由」を読む\n" +
                    "手順4. 心当たりがないものは「設定で無効化」からOFFにする\n" +
                    "手順5. 必要なら「この機能を使えるアプリも見る」で未使用分も確認")

                section("権限監査の見方",
                    "リスク判定の基準:\n" +
                    "・危険 … 画面読み取り＋操作代行が可能で、かつストア外から導入された\n" +
                    "・高 … 画面読み取りと操作代行の両方が可能\n" +
                    "・中 … 読み取りまたは操作のどちらかが可能\n" +
                    "・低 … 標準アプリ、既知の支援アプリ、または未使用\n\n" +
                    "加点要素: 画面の読み取り、操作の代行、キー入力の監視、" +
                    "対象アプリの限定なし（銀行アプリを含む全画面で動作）、ストア外からの導入。\n" +
                    "減点要素: 端末標準のアプリ、一般的な画面読み上げ・パスワード管理アプリ。\n\n" +
                    "【原則】支援機能とパスワード管理以外で、この権限を求めるアプリは基本的に不要です。" +
                    "「動作に必要」と説明されても安易に許可せず、" +
                    "使い終わったらOFFに戻す運用が最も安全です。")


                section("その他｜これは何？",
                    "分類しきれない機能をまとめています。" +
                    "『開く』を押すと専用画面が開きます。")

                section("① 外部メディア検査の手順",
                    "手順1. USBメモリやSDカードを端末に接続する（USBは変換アダプタが必要）\n" +
                    "手順2. ツール→外部メディア検査→「フォルダを選んで検査」\n" +
                    "手順3. 表示された画面で、USBメモリのフォルダを選んで「このフォルダを使用」\n" +
                    "手順4. 検査結果が一覧表示される\n\n" +
                    "見方: 赤【危険】は開かないでください。特に" +
                    "『二重拡張子』『文字の並びを逆転させる特殊文字』『拡張子と中身の不一致』は" +
                    "意図的な偽装であり、事故ではありません。\n" +
                    "黄【注意】は形式として危険なだけで、心当たりがあれば問題ない場合もあります。\n\n" +
                    "限界: ウイルス定義を持たないため、既知ウイルスかどうかの判定はできません。" +
                    "『安全と出た＝ウイルスがない』ではありません。")

                section("② SaaS接続確認の手順",
                    "手順1. 自宅など信頼できる回線に接続する\n" +
                    "手順2. サービス名とログインURLを入力して「この設定を追加」\n" +
                    "手順3. 「基準登録」を押す（接続先と証明書が記録されます）\n" +
                    "手順4. 外出先の無料Wi-Fiに接続する\n" +
                    "手順5. 「今すぐ確認」を押す\n" +
                    "手順6. 判定が『正常』ならリンクを開いてよい。『危険』なら開かない\n\n" +
                    "見方:\n" +
                    "・正常 = 接続先も証明書も基準と一致\n" +
                    "・危険（接続先が違う）= 別サイトへ誘導されています。偽アクセスポイントや" +
                    "Wi-Fiの認証画面の割り込みが疑われます\n" +
                    "・危険（証明書が違う）= 通信を盗み見られている可能性（中間者攻撃）\n" +
                    "・注意（接続失敗）= Wi-Fiのログイン手続きが未完了の可能性\n\n" +
                    "注意: サイト側が証明書を正規に更新したときも『違う』と出ます。" +
                    "安全な回線で確認して問題なければ、もう一度「基準登録」を押して更新してください。")

                section("③ Wi-Fi／ルーター診断の手順",
                    "手順1. 診断したいWi-Fi（自宅など自分が管理する回線）に接続\n" +
                    "手順2. 「診断を実行」→位置情報の許可を求められたら許可\n" +
                    "　（AndroidではWi-Fi名の取得に位置情報権限が必要な仕様のためです）\n" +
                    "手順3. 総合評価と×項目を確認\n" +
                    "手順4. ルーターの管理画面（取扱説明書記載のアドレス）で設定を修正\n\n" +
                    "見方:\n" +
                    "・暗号方式: WPA3かWPA2なら良好。WEPや暗号化なしは至急変更\n" +
                    "・開いているポート: Telnet(23)・FTP(21)・SMB(445)・ADB(5555)は特に危険\n" +
                    "・DNS: 見覚えのない外部サーバーが設定されていたら乗っ取りの疑い\n" +
                    "・総合評価: A（良好）／B（要改善）／C（要対策）\n\n" +
                    "注意: 必ず自分が管理する回線でのみ実行してください。" +
                    "他人のネットワークへのポート確認は法的な問題になり得ます。\n" +
                    "また、管理画面のパスワード強度やファームウェアの中身までは確認できません。")

                section("④ 機器バージョン台帳の手順",
                    "手順1. 機器名・種別・現在のバージョンを入力\n" +
                    "手順2. メーカーの更新情報ページのURLを入力して「台帳に追加」\n" +
                    "手順3. ときどき「更新確認」を押す\n" +
                    "手順4. 変化があるとカードが赤くなり、前回との差分が表示される\n" +
                    "手順5. 実際に機器を更新したら「台帳を最新に更新」で記録を合わせる\n\n" +
                    "見方: ページ内の『Ver 1.23』のような表記を自動で拾い、" +
                    "最も新しい番号を『ページ上の最新』として表示します。" +
                    "表記を拾えない場合も、ページ内容が変わったこと自体は検知します。\n\n" +
                    "抽出パターン欄（上級者向け）: 正規表現で拾い方を指定できます。" +
                    "空欄なら既定のパターンが使われます。")

                section("通信について（重要）",
                    "②SaaS接続確認と④更新確認は、インターネット接続を使います。" +
                    "接続先は利用者が登録したURLのみで、当アプリの開発者や第三者へは" +
                    "何も送信されません。①外部メディア検査と③ルーター診断は" +
                    "外部への通信を行いません。")

                section("関所（漏洩ガード）｜これは何？",
                    "他のアプリからテキストや画像を共有するときに一度経由させる『検問所』です。" +
                    "マイナンバーやカード番号、機密キーワードが含まれていないか転送前に検査します。" +
                    "SNSへのうっかり投稿対策の中核機能です。")

                section("使い方手順",
                    "手順1. メモ帳・ブラウザ等、任意のアプリでテキスト（または画像）を選ぶ\n" +
                    "手順2. 「共有」ボタンを押す\n" +
                    "手順3. 共有先の一覧から『守りのDX関所』を選ぶ\n" +
                    "手順4. 検査結果画面が表示される\n" +
                    "手順5. 検出があった場合は内容を確認し、次のいずれかを選ぶ\n" +
                    "　・マスクして転送（推奨）: 番号を***化してから送る\n" +
                    "　・そのまま転送（自己責任）\n" +
                    "　・中止\n" +
                    "手順6. 転送先選択画面で、本来送りたかったアプリを選ぶ\n\n" +
                    "動作テスト: メモに 123456789018 と書いて共有→関所を選ぶと" +
                    "「マイナンバーの可能性」が表示されます（これは公開されているテスト用番号です）。")

                section("見方（検査結果画面）",
                    "・「✓ 機密情報は検出されませんでした」（緑）=そのまま転送して問題なし\n" +
                    "・「⚠ N件の注意事項」（赤）=下に検出内容のカードが並ぶ\n\n" +
                    "検出カードの種類:\n" +
                    "・【マイナンバー】12桁を検査番号まで検証（単なる12桁数字では反応しません）\n" +
                    "・【クレジットカード】14〜16桁をLuhn方式で検証\n" +
                    "・【機密キーワード】社外秘/部外秘/極秘/マル秘/機密/取扱注意/Confidential等\n" +
                    "・【位置情報】画像のEXIFに撮影場所のGPSが残っている\n\n" +
                    "SNSアプリ（X/Instagram/Facebook/LINE/TikTok）が端末にある場合、" +
                    "検出時に追加の警告が表示されます。")

                section("よくある質問",
                    "Q. 共有シートに関所が出ない\n" +
                    "A. 共有するデータがテキストか画像のときだけ表示されます。一覧の「その他」に隠れている場合もあります。\n\n" +
                    "Q. 検査内容はどこかに送られる？\n" +
                    "A. 送られません。検査はすべて端末内で完結し、履歴も保存しません。")
            }
        }

        outer.addView(ScrollView(this).apply { addView(list) })
        return outer
    }

    // =========================================================
    // タブ3: 通信ログ（Phase 2 / A案・記録専用VPN）
    // =========================================================
    private fun buildCommsView(): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }

        val active = DnsMonitorService.isRunning

        // 記録の開始/停止カード
        list.addView(card().apply {
            addView(TextView(this@MainActivity).apply {
                text = if (active) "● 通信を記録中" else "○ 記録は停止中"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (active) greenColor else subColor)
            })
            addView(TextView(this@MainActivity).apply {
                text = "記録専用モードです。記録中は端末の通信が流れません。" +
                    "通信先を棚卸ししたい時だけONにし、終わったらOFFにしてください。"
                textSize = 12f
                setTextColor(subColor)
                setPadding(0, dp(6), 0, dp(8))
            })
            addView(Button(this@MainActivity).apply {
                text = if (active) "記録を停止" else "記録を開始"
                textSize = 14f
                isAllCaps = false
                setTextColor(Color.BLACK)
                setBackgroundColor(if (active) yellowColor else greenColor)
                setOnClickListener {
                    if (active) stopVpn() else startVpn()
                }
            })
        })

        val entries = DnsLogStore.snapshot()

        // サマリー＋クリア
        list.addView(card().apply {
            addView(row("記録済みドメイン", "${entries.size} 件", accentColor))
            addView(Button(this@MainActivity).apply {
                text = "ログを消去"
                textSize = 13f
                isAllCaps = false
                setTextColor(textColor)
                setBackgroundColor(bgColor)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
                setOnClickListener {
                    DnsLogStore.clear(applicationContext)
                    renderContent()
                }
            })
        })

        if (entries.isEmpty()) {
            list.addView(card().apply {
                addView(TextView(this@MainActivity).apply {
                    text = "まだ記録がありません。\n「記録を開始」を押してVPNを許可し、" +
                        "しばらく他アプリを使うと、通信先ドメインがここに一覧表示されます。"
                    textSize = 13f
                    setTextColor(textColor)
                })
            })
        } else {
            entries.forEach { e ->
                val policy = DnsLogStore.getPolicy(e.domain)
                list.addView(card().apply {
                    addView(TextView(this@MainActivity).apply {
                        text = e.domain
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(textColor)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = "問い合わせ ${e.count} 回"
                        textSize = 12f
                        setTextColor(subColor)
                        setPadding(0, dp(2), 0, dp(6))
                    })
                    addView(buildPolicyRow(e.domain, policy))
                })
            }
        }

        return ScrollView(this).apply { addView(list) }
    }

    private fun buildPolicyRow(domain: String, current: Int): View {
        val rowL = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val labels = listOf(
            "許可" to DnsLogStore.POLICY_ALLOW,
            "記録のみ" to DnsLogStore.POLICY_RECORD,
            "ブロック" to DnsLogStore.POLICY_BLOCK
        )
        labels.forEach { (label, value) ->
            rowL.addView(Button(this).apply {
                text = label
                textSize = 12f
                isAllCaps = false
                val selected = current == value
                setTextColor(if (selected) Color.BLACK else textColor)
                setBackgroundColor(
                    if (selected) when (value) {
                        DnsLogStore.POLICY_ALLOW -> greenColor
                        DnsLogStore.POLICY_BLOCK -> redColor
                        else -> accentColor
                    } else bgColor
                )
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    DnsLogStore.setPolicy(applicationContext, domain, value)
                    if (value == DnsLogStore.POLICY_BLOCK) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("ブロック設定を記録しました")
                            .setMessage("このドメインを「ブロック」に指定しました。" +
                                "現バージョン(A案)ではポリシーの記録のみで、実際の遮断は行いません。" +
                                "遮断の実効化はPhase 2.5で対応予定です。")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    renderContent()
                }
            })
        }
        return rowL
    }

    // =========================================================
    // タブ4: 緊急対応（怪しいリンクを踏んだ時）
    // =========================================================
    private var emergencyHours = 24
    private var emergencyScanned = false
    private var urlInputText = ""

    private fun buildEmergencyView(): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }

        // ---------- ① リンク検査 ----------
        list.addView(card().apply {
            addView(TextView(this@MainActivity).apply {
                text = "① リンクを開く前に検査"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(accentColor)
            })
            addView(TextView(this@MainActivity).apply {
                text = "怪しいURLを貼り付けて検査します。端末内だけで判定し、URLを外部に送信しません。"
                textSize = 12f
                setTextColor(subColor)
                setPadding(0, dp(4), 0, dp(8))
            })
            val input = EditText(this@MainActivity).apply {
                hint = "https://..."
                textSize = 13f
                setTextColor(textColor)
                setHintTextColor(subColor)
                setBackgroundColor(bgColor)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setText(urlInputText)
            }
            addView(input)
            addView(Button(this@MainActivity).apply {
                text = "このリンクを検査"
                textSize = 14f
                isAllCaps = false
                setTextColor(Color.BLACK)
                setBackgroundColor(accentColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
                ).apply { topMargin = dp(8) }
                setOnClickListener {
                    urlInputText = input.text.toString()
                    if (urlInputText.isBlank()) {
                        Toast.makeText(this@MainActivity,
                            "URLを入力してください", Toast.LENGTH_SHORT).show()
                    } else {
                        showUrlResult(UrlChecker.analyze(urlInputText))
                    }
                }
            })
        })

        // ---------- ② 侵害チェック ----------
        list.addView(card().apply {
            addView(TextView(this@MainActivity).apply {
                text = "② 踏んでしまった後の侵害チェック"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(accentColor)
            })
            addView(TextView(this@MainActivity).apply {
                text = "乗っ取りの足がかりになる設定を一括で確認します。\n対象期間: 直近" +
                    (if (emergencyHours == 24) "24時間" else "7日間")
                textSize = 12f
                setTextColor(subColor)
                setPadding(0, dp(4), 0, dp(8))
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                listOf("直近24時間" to 24, "直近7日間" to 168).forEach { (lbl, h) ->
                    addView(Button(this@MainActivity).apply {
                        text = lbl
                        textSize = 12f
                        isAllCaps = false
                        val sel = emergencyHours == h
                        setTextColor(if (sel) Color.BLACK else textColor)
                        setBackgroundColor(if (sel) accentColor else bgColor)
                        layoutParams = LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            setMargins(dp(2), 0, dp(2), 0)
                        }
                        setOnClickListener {
                            emergencyHours = h
                            emergencyScanned = true
                            renderContent()
                        }
                    })
                }
            })
            addView(Button(this@MainActivity).apply {
                text = "侵害チェックを実行"
                textSize = 14f
                isAllCaps = false
                setTextColor(Color.BLACK)
                setBackgroundColor(redColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
                ).apply { topMargin = dp(8) }
                setOnClickListener {
                    emergencyScanned = true
                    renderContent()
                }
            })
        })

        if (emergencyScanned) {
            val threats = ThreatScanner.scan(applicationContext, emergencyHours)
            val crit = threats.count { it.severity == ThreatScanner.SEV_CRIT }

            list.addView(card().apply {
                addView(TextView(this@MainActivity).apply {
                    text = when {
                        threats.isEmpty() -> "✓ 気になる項目は見つかりませんでした"
                        crit > 0 -> "⚠ 至急確認すべき項目が ${crit} 件あります"
                        else -> "確認をおすすめする項目が ${threats.size} 件あります"
                    }
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(when {
                        threats.isEmpty() -> greenColor
                        crit > 0 -> redColor
                        else -> yellowColor
                    })
                })
                if (threats.isEmpty()) {
                    addView(TextView(this@MainActivity).apply {
                        text = "ただし、この検査は端末側の変化を見るものです。" +
                            "偽サイトでIDやパスワードを入力してしまった場合は、" +
                            "下の応急処置に沿ってパスワード変更を必ず行ってください。"
                        textSize = 12f
                        setTextColor(subColor)
                        setPadding(0, dp(6), 0, 0)
                    })
                }
            })

            threats.forEach { t ->
                val sevColor = when (t.severity) {
                    ThreatScanner.SEV_CRIT -> redColor
                    ThreatScanner.SEV_WARN -> yellowColor
                    else -> subColor
                }
                list.addView(card().apply {
                    addView(TextView(this@MainActivity).apply {
                        text = "【${t.category}】"
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(sevColor)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = t.title
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(textColor)
                        setPadding(0, dp(2), 0, dp(2))
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = t.detail
                        textSize = 12f
                        setTextColor(textColor)
                    })
                    val aLabel = t.actionLabel
                    if (aLabel != null) {
                        addView(Button(this@MainActivity).apply {
                            text = aLabel
                            textSize = 13f
                            isAllCaps = false
                            setTextColor(Color.BLACK)
                            setBackgroundColor(sevColor)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)
                            ).apply { topMargin = dp(8) }
                            setOnClickListener { runThreatAction(t) }
                        })
                    }
                })
            }
        }

        // ---------- ③ 応急処置チェックリスト ----------
        list.addView(card().apply {
            addView(TextView(this@MainActivity).apply {
                text = "③ 応急処置チェックリスト"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(accentColor)
            })
            addView(TextView(this@MainActivity).apply {
                text = "リンクを踏んでしまった直後は、上から順に対応してください。"
                textSize = 12f
                setTextColor(subColor)
                setPadding(0, dp(4), 0, 0)
            })
        })

        data class Step(val no: Int, val title: String, val body: String,
                        val btn: String?, val act: (() -> Unit)?)

        val steps = listOf(
            Step(1, "通信を止める",
                "まず機内モードをONにして、情報の送信と追加ダウンロードを止めます。" +
                "既にアプリを入れてしまった場合は特に有効です。",
                "無線とネットワークの設定を開く",
                { openSafely(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }),
            Step(2, "入れてしまったアプリを削除",
                "リンク先から案内されたアプリ（APK）をインストールした場合は、ただちに削除します。" +
                "上の②侵害チェックで最近入ったアプリを確認できます。",
                "アプリ一覧を開く",
                { openSafely(Intent(Settings.ACTION_APPLICATION_SETTINGS)) }),
            Step(3, "ユーザー補助・通知アクセスを確認",
                "不正アプリはこの2つを悪用して、入力の盗み見や認証コードの窃取を行います。" +
                "身に覚えのないアプリがONなら即OFFにしてください。",
                "ユーザー補助の設定を開く",
                { openSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }),
            Step(4, "提供元不明アプリの許可を取り消す",
                "ブラウザ等に『不明なアプリのインストール』が許可されていると、" +
                "再び勝手にアプリを入れられる恐れがあります。",
                "インストール許可の設定を開く",
                {
                    openSafely(Intent(
                        if (Build.VERSION.SDK_INT >= 26)
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                        else Settings.ACTION_SECURITY_SETTINGS))
                }),
            Step(5, "パスワードを変更する",
                "偽サイトでIDやパスワードを入力してしまった場合は、" +
                "その場で必ず変更します。**別の安全な端末から**行うのが理想です。" +
                "同じパスワードを使い回している他のサービスもすべて変更してください。",
                null, null),
            Step(6, "二段階認証を有効にする",
                "パスワードが漏れても、二段階認証があれば侵入を防げます。" +
                "SMSよりも認証アプリの方が安全です。",
                null, null),
            Step(7, "カード情報を入力した場合",
                "カード会社に連絡して利用停止・再発行を依頼します。" +
                "カード裏面の番号へ。あわせて利用明細を確認してください。",
                null, null),
            Step(8, "ブラウザの履歴とCookieを消す",
                "偽サイトに残ったセッション情報を無効化します。" +
                "ブラウザの設定→プライバシー→閲覧データの削除。",
                null, null),
            Step(9, "端末を最新にする",
                "OSとブラウザを最新に更新し、既知の脆弱性を塞ぎます。" +
                "端末診断タブでパッチ日を確認できます。",
                "セキュリティ設定を開く",
                { openSafely(Intent(Settings.ACTION_SECURITY_SETTINGS)) }),
            Step(10, "会社の端末なら必ず報告",
                "隠すと被害が拡大します。情報システム部門や上長へ速やかに連絡してください。" +
                "『踏んだ日時・URL・入力した情報の有無・入れたアプリ』を伝えると対応が早くなります。",
                null, null)
        )

        steps.forEach { s ->
            list.addView(card().apply {
                addView(TextView(this@MainActivity).apply {
                    text = "手順${s.no}. ${s.title}"
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(textColor)
                })
                addView(TextView(this@MainActivity).apply {
                    text = s.body
                    textSize = 12f
                    setTextColor(subColor)
                    setPadding(0, dp(4), 0, 0)
                })
                val btnLabel = s.btn
                val btnAct = s.act
                if (btnLabel != null && btnAct != null) {
                    addView(Button(this@MainActivity).apply {
                        text = btnLabel
                        textSize = 13f
                        isAllCaps = false
                        setTextColor(textColor)
                        setBackgroundColor(bgColor)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dp(42)
                        ).apply { topMargin = dp(8) }
                        setOnClickListener { btnAct() }
                    })
                }
            })
        }

        return ScrollView(this).apply { addView(list) }
    }

    private fun showUrlResult(r: UrlChecker.Result) {
        val sb = StringBuilder()
        sb.append(when (r.level) {
            UrlChecker.LEVEL_DANGER -> "判定: 危険（開かないでください）\n\n"
            UrlChecker.LEVEL_CAUTION -> "判定: 注意\n\n"
            else -> "判定: 明らかな危険信号なし\n\n"
        })
        if (r.host.isNotEmpty()) sb.append("実際の接続先: ${r.host}\n\n")
        if (r.findings.isEmpty()) {
            sb.append("既知の詐欺パターンには該当しませんでした。\n\n" +
                "ただし『危険信号が無い＝安全』ではありません。" +
                "新しい詐欺サイトや、正規サイトが乗っ取られている場合は検出できません。" +
                "ログインやカード番号の入力を求められたら、リンクからではなく" +
                "公式アプリやブックマークから改めてアクセスしてください。")
        } else {
            r.findings.forEach {
                sb.append(if (it.severity == 2) "【危険】" else "【注意】")
                sb.append("${it.title}\n${it.detail}\n\n")
            }
        }
        AlertDialog.Builder(this)
            .setTitle("リンク検査結果")
            .setMessage(sb.toString().trim())
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun runThreatAction(t: ThreatScanner.Threat) {
        try {
            when (t.actionKind) {
                "uninstall" -> {
                    val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:${t.pkg}"))
                    startActivity(i)
                }
                "app_detail" -> {
                    val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${t.pkg}"))
                    startActivity(i)
                }
                "settings" -> {
                    openSafely(Intent(t.settingsAction))
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "設定画面を開けませんでした。端末の設定から手動で確認してください。",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun openSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "この端末ではこの設定画面を直接開けません。" +
                "設定アプリから手動で開いてください。", Toast.LENGTH_LONG).show()
        }
    }

    // =========================================================
    // タブ5: ツール（Phase 5〜8 の入口）
    // =========================================================
    private fun buildToolsMenuView(): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }

        val tools = listOf(
            Triple(0, "外部メディア検査",
                "USBメモリやSDカードの中身を調べ、実行ファイル・二重拡張子・" +
                "ファイル名偽装・拡張子と中身の不一致を指摘します。"),
            Triple(1, "SaaS接続確認",
                "契約中のSaaSのログイン先を登録し、公衆Wi-Fiで別サイトへの" +
                "リダイレクトや証明書のすり替えが起きていないか照合します。"),
            Triple(2, "Wi-Fi／ルーター診断",
                "接続中のWi-Fiの暗号方式、ルーターの開放ポート、DNSの向き先を" +
                "確認して総合評価します。自宅の回線で実行してください。"),
            Triple(3, "機器バージョン台帳",
                "ルーターやPCのバージョンを記録し、メーカーの更新情報ページの" +
                "変化を検知して知らせます。")
        )

        tools.forEach { (idx, title, desc) ->
            list.addView(card().apply {
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(accentColor)
                })
                addView(TextView(this@MainActivity).apply {
                    text = desc
                    textSize = 13f
                    setTextColor(textColor)
                    setPadding(0, dp(6), 0, dp(4))
                })
                addView(Button(this@MainActivity).apply {
                    text = "開く"
                    textSize = 14f
                    isAllCaps = false
                    setTextColor(Color.BLACK)
                    setBackgroundColor(accentColor)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
                    ).apply { topMargin = dp(6) }
                    setOnClickListener {
                        try {
                            startActivity(Intent(this@MainActivity, ToolsActivity::class.java)
                                .putExtra(ToolsActivity.EXTRA_PAGE, idx))
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity,
                                "画面を開けませんでした", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
                setOnClickListener {
                    try {
                        startActivity(Intent(this@MainActivity, ToolsActivity::class.java)
                            .putExtra(ToolsActivity.EXTRA_PAGE, idx))
                    } catch (e: Exception) { }
                }
            })
        }

        list.addView(card().apply {
            addView(TextView(this@MainActivity).apply {
                text = "これらの機能はインターネット接続を使います（SaaS接続確認・台帳の更新確認）。" +
                    "接続先は利用者が登録したURLのみで、当アプリの開発者へは何も送信されません。"
                textSize = 12f
                setTextColor(subColor)
            })
        })

        return ScrollView(this).apply { addView(list) }
    }

    // =========================================================
    // タブ5: パソコン（利用目的別セキュリティ点検）
    // =========================================================
    private var pcOs = -1
    private var pcPurposes: MutableSet<Int>? = null
    private var pcChecked: MutableSet<String>? = null
    private var pcReport: PcAdvisor.Report? = null

    private fun buildPcView(): View {
        if (pcOs < 0) pcOs = PcAdvisor.loadOs(applicationContext)
        val purposes = pcPurposes ?: PcAdvisor.loadPurposes(applicationContext)
            .also { pcPurposes = it }
        val checked = pcChecked ?: PcAdvisor.loadChecked(applicationContext)
            .also { pcChecked = it }

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }

        // ---- 説明 ----
        list.addView(card().apply {
            addView(TextView(this@MainActivity).apply {
                text = "パソコンのセキュリティ点検"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(accentColor)
            })
            addView(TextView(this@MainActivity).apply {
                text = "そのパソコンの使い方に応じて、必要な対策が変わります。" +
                    "OSと利用目的を選び、できている項目にチェックを入れて" +
                    "「評価する」を押すと、優先順位つきの提言が出ます。\n\n" +
                    "手順1. OSを選ぶ\n手順2. 利用目的を選ぶ（複数選択できます）\n" +
                    "手順3. できている項目にチェック\n手順4. 「評価する」を押す"
                textSize = 13f
                setTextColor(textColor)
                setPadding(0, dp(6), 0, 0)
            })
        })

        // ---- OS選択 ----
        list.addView(card().apply {
            addView(TextView(this@MainActivity).apply {
                text = "OS"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(textColor)
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
                listOf("Windows" to PcAdvisor.OS_WIN, "Mac" to PcAdvisor.OS_MAC)
                    .forEach { (label, v) ->
                        addView(Button(this@MainActivity).apply {
                            text = label
                            textSize = 13f
                            isAllCaps = false
                            val sel = pcOs == v
                            setTextColor(if (sel) Color.BLACK else textColor)
                            setBackgroundColor(if (sel) accentColor else bgColor)
                            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                                setMargins(dp(3), 0, dp(3), 0)
                            }
                            setOnClickListener {
                                pcOs = v
                                PcAdvisor.saveOs(applicationContext, v)
                                renderContent()
                            }
                        })
                    }
            })
        })

        // ---- 利用目的 ----
        list.addView(card().apply {
            addView(TextView(this@MainActivity).apply {
                text = "利用目的（複数選択可）"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(textColor)
            })
            listOf(
                PcAdvisor.P_OUTDOOR to "屋外利用（持ち出す）",
                PcAdvisor.P_CONFIDENTIAL to "機密情報を保存する",
                PcAdvisor.P_SHARED to "複数人でシェアする"
            ).forEach { (v, label) ->
                addView(Button(this@MainActivity).apply {
                    val sel = purposes.contains(v)
                    text = (if (sel) "✓ " else "　") + label
                    textSize = 13f
                    isAllCaps = false
                    gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
                    setPadding(dp(12), 0, dp(8), 0)
                    setTextColor(if (sel) Color.BLACK else textColor)
                    setBackgroundColor(if (sel) greenColor else bgColor)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
                    ).apply { topMargin = dp(6) }
                    setOnClickListener {
                        if (sel) purposes.remove(v) else purposes.add(v)
                        PcAdvisor.savePurposes(applicationContext, purposes)
                        pcReport = null
                        renderContent()
                    }
                })
            }
        })

        if (purposes.isEmpty()) {
            list.addView(card().apply {
                addView(TextView(this@MainActivity).apply {
                    text = "利用目的を1つ以上選んでください。" +
                        "選んだ目的に応じたチェック項目が表示されます。"
                    textSize = 13f
                    setTextColor(yellowColor)
                })
            })
            return ScrollView(this).apply { addView(list) }
        }

        // ---- 評価結果（押した直後に見えるよう上部に配置） ----
        val rep = pcReport
        if (rep != null) {
            val gc = when {
                rep.grade.startsWith("A") -> greenColor
                rep.grade.startsWith("B") -> yellowColor
                else -> redColor
            }
            list.addView(card().apply {
                addView(TextView(this@MainActivity).apply {
                    text = "評価: ${rep.grade}"
                    textSize = 20f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(gc)
                    gravity = Gravity.CENTER
                })
                addView(TextView(this@MainActivity).apply {
                    text = "達成度 ${rep.percent}%（重要度で重みづけ）\n" +
                        "対応済み ${rep.doneCount} / ${rep.totalCount} 項目\n" +
                        "対象: " + purposes.sorted()
                            .joinToString("・") { PcAdvisor.purposeName(it) }
                    textSize = 12f
                    setTextColor(subColor)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(6), 0, dp(8))
                })
                addView(TextView(this@MainActivity).apply {
                    text = rep.summary
                    textSize = 13f
                    setTextColor(textColor)
                })
            })

            fun adviceBlock(title: String, color: Int, items: List<PcAdvisor.Item>) {
                if (items.isEmpty()) return
                list.addView(card().apply {
                    addView(TextView(this@MainActivity).apply {
                        text = "$title（${items.size}件）"
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(color)
                    })
                })
                items.forEachIndexed { i, item ->
                    list.addView(card().apply {
                        addView(TextView(this@MainActivity).apply {
                            text = "${i + 1}. ${item.title}"
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(color)
                        })
                        addView(TextView(this@MainActivity).apply {
                            text = "なぜ必要か: ${item.why}"
                            textSize = 12f
                            setTextColor(subColor)
                            setPadding(0, dp(4), 0, dp(4))
                        })
                        addView(TextView(this@MainActivity).apply {
                            text = "対処: ${item.advice(pcOs)}"
                            textSize = 13f
                            setTextColor(textColor)
                        })
                    })
                }
            }

            adviceBlock("最優先で対応（必須）", redColor, rep.musts)
            adviceBlock("次に対応（重要）", yellowColor, rep.importants)
            adviceBlock("余裕があれば（推奨）", accentColor, rep.recommends)

            if (rep.musts.isEmpty() && rep.importants.isEmpty() &&
                rep.recommends.isEmpty()) {
                list.addView(card().apply {
                    addView(TextView(this@MainActivity).apply {
                        text = "未対応の項目はありません。年1回の見直しと、" +
                            "利用目的が変わったときの再評価をおすすめします。"
                        textSize = 13f
                        setTextColor(greenColor)
                    })
                })
            }

            list.addView(card().apply {
                addView(TextView(this@MainActivity).apply {
                    text = "この評価は自己申告に基づく目安です。" +
                        "実際の設定値の確認や、社内規程との整合はあらためて点検してください。"
                    textSize = 12f
                    setTextColor(subColor)
                })
            })
        }

        // ---- チェックリスト ----
        val items = PcAdvisor.itemsFor(purposes)
        val groups = listOf(PcAdvisor.P_COMMON) + purposes.sorted()
        groups.forEach { g ->
            val groupItems = items.filter { it.purpose == g }
            if (groupItems.isEmpty()) return@forEach
            list.addView(card().apply {
                setBackgroundColor(bgColor)
                addView(TextView(this@MainActivity).apply {
                    text = "■ " + PcAdvisor.purposeName(g) +
                        "（${groupItems.count { checked.contains(it.id) }}/${groupItems.size}）"
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(accentColor)
                })
            })
            groupItems.forEach { item ->
                val badge = when (item.weight) {
                    PcAdvisor.W_MUST -> "必須"
                    PcAdvisor.W_IMPORTANT -> "重要"
                    else -> "推奨"
                }
                val badgeColor = when (item.weight) {
                    PcAdvisor.W_MUST -> redColor
                    PcAdvisor.W_IMPORTANT -> yellowColor
                    else -> subColor
                }
                list.addView(card().apply {
                    addView(TextView(this@MainActivity).apply {
                        text = "[$badge]"
                        textSize = 11f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(badgeColor)
                    })
                    addView(CheckBox(this@MainActivity).apply {
                        text = item.title
                        textSize = 14f
                        setTextColor(textColor)
                        isChecked = checked.contains(item.id)
                        setOnCheckedChangeListener { _, isOn ->
                            if (isOn) checked.add(item.id) else checked.remove(item.id)
                            PcAdvisor.saveChecked(applicationContext, checked)
                        }
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = item.why
                        textSize = 12f
                        setTextColor(subColor)
                        setPadding(dp(4), 0, 0, 0)
                    })
                })
            }
        }

        // ---- 評価ボタン ----
        list.addView(Button(this).apply {
            text = "評価する"
            textSize = 16f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            setBackgroundColor(accentColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)
            ).apply { topMargin = dp(16) }
            setOnClickListener {
                pcReport = PcAdvisor.evaluate(purposes, checked)
                renderContent()
            }
        })

        list.addView(Button(this).apply {
            text = "チェックをすべて外す"
            textSize = 13f
            isAllCaps = false
            setTextColor(textColor)
            setBackgroundColor(cardColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
            ).apply { topMargin = dp(8) }
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("確認")
                    .setMessage("チェックをすべて外しますか？")
                    .setPositiveButton("外す") { _, _ ->
                        checked.clear()
                        PcAdvisor.saveChecked(applicationContext, checked)
                        pcReport = null
                        renderContent()
                    }
                    .setNegativeButton("やめる", null)
                    .show()
            }
        })

        return ScrollView(this).apply { addView(list) }
    }

    // =========================================================
    // タブ0: 状態（端末の現況）
    // =========================================================
    private val REQ_STATUS_PERM = 3001

    private fun buildStatusView(): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }

        val hasLocation = checkSelfPermission(
            android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasBt = if (Build.VERSION.SDK_INT >= 31)
            checkSelfPermission("android.permission.BLUETOOTH_CONNECT") ==
                PackageManager.PERMISSION_GRANTED
        else true

        list.addView(Button(this).apply {
            text = "最新の状態に更新"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.BLACK)
            setBackgroundColor(accentColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
            ).apply { topMargin = dp(8) }
            setOnClickListener { renderContent() }
        })

        // ---------- ストレージ ----------
        val st = DeviceStatus.storage(applicationContext)
        list.addView(card().apply {
            addView(sectionTitle("ストレージ"))
            val pctColor = when {
                st.percent >= 90 -> redColor
                st.percent >= 75 -> yellowColor
                else -> greenColor
            }
            addView(TextView(this@MainActivity).apply {
                text = "${DeviceStatus.formatBytes(st.usedBytes)} / " +
                    DeviceStatus.formatBytes(st.totalBytes)
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(textColor)
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = "使用率 ${st.percent}%　空き ${DeviceStatus.formatBytes(st.freeBytes)}"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(pctColor)
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, dp(8))
            })
            // 簡易バー
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(14))
                addView(View(this@MainActivity).apply {
                    setBackgroundColor(pctColor)
                    layoutParams = LinearLayout.LayoutParams(
                        0, dp(14), st.percent.coerceIn(1, 100).toFloat())
                })
                addView(View(this@MainActivity).apply {
                    setBackgroundColor(bgColor)
                    layoutParams = LinearLayout.LayoutParams(
                        0, dp(14), (100 - st.percent).coerceIn(0, 99).toFloat())
                })
            })
            st.volumes.forEach { v ->
                addView(TextView(this@MainActivity).apply {
                    text = "・${v.name}: ${DeviceStatus.formatBytes(v.usedBytes)} / " +
                        DeviceStatus.formatBytes(v.totalBytes)
                    textSize = 12f
                    setTextColor(subColor)
                    setPadding(0, dp(6), 0, 0)
                })
            }
            if (st.percent >= 90) {
                addView(TextView(this@MainActivity).apply {
                    text = "空き容量が不足しています。OSの更新が適用できなくなることがあるため、" +
                        "不要なファイルを整理してください。"
                    textSize = 12f
                    setTextColor(redColor)
                    setPadding(0, dp(6), 0, 0)
                })
            }
        })

        // ---------- Androidバージョン ----------
        val ver = DeviceStatus.version()
        list.addView(card().apply {
            addView(sectionTitle("Androidバージョン"))
            addView(TextView(this@MainActivity).apply {
                text = "${ver.codeName}（${ver.release}）"
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (ver.needsAttention) yellowColor else greenColor)
                setPadding(0, dp(6), 0, dp(4))
            })
            addView(TextView(this@MainActivity).apply {
                text = "機種: ${ver.manufacturer} ${ver.model}\n" +
                    "ビルド: ${ver.buildId}\n" +
                    "セキュリティパッチ: ${ver.securityPatch}" +
                    (if (ver.patchAgeDays >= 0) "（${ver.patchAgeDays}日前）" else "")
                textSize = 12f
                setTextColor(subColor)
            })
            addView(TextView(this@MainActivity).apply {
                text = ver.updateHint
                textSize = 13f
                setTextColor(textColor)
                setPadding(0, dp(8), 0, 0)
            })
            addView(Button(this@MainActivity).apply {
                text = "システム更新の画面を開く"
                textSize = 13f
                isAllCaps = false
                setTextColor(Color.BLACK)
                setBackgroundColor(accentColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
                ).apply { topMargin = dp(8) }
                setOnClickListener { openSystemUpdate() }
            })
        })

        // ---------- Wi-Fi ----------
        val wifi = DeviceStatus.wifi(applicationContext, hasLocation)
        list.addView(card().apply {
            addView(sectionTitle("接続中のWi-Fi"))
            addView(TextView(this@MainActivity).apply {
                text = if (wifi.connected) wifi.ssid else "未接続"
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (wifi.connected) greenColor else subColor)
                setPadding(0, dp(6), 0, dp(4))
            })
            if (wifi.connected) {
                addView(TextView(this@MainActivity).apply {
                    text = listOf(
                        if (wifi.security.isNotEmpty()) "暗号方式: ${wifi.security}" else "",
                        if (wifi.linkSpeed.isNotEmpty()) "リンク速度: ${wifi.linkSpeed}" else "",
                        if (wifi.ip.isNotEmpty()) "IPアドレス: ${wifi.ip}" else ""
                    ).filter { it.isNotEmpty() }.joinToString("\n")
                    textSize = 12f
                    setTextColor(subColor)
                })
            }
            addView(TextView(this@MainActivity).apply {
                text = wifi.note
                textSize = 12f
                setTextColor(if (wifi.connected) subColor else yellowColor)
                setPadding(0, dp(6), 0, 0)
            })
            if (!hasLocation) {
                addView(Button(this@MainActivity).apply {
                    text = "位置情報を許可してWi-Fi名を表示"
                    textSize = 13f
                    isAllCaps = false
                    setTextColor(Color.BLACK)
                    setBackgroundColor(yellowColor)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
                    ).apply { topMargin = dp(8) }
                    setOnClickListener { requestStatusPermissions() }
                })
            }
        })

        // ---------- Bluetooth ----------
        val bt = DeviceStatus.bluetooth(applicationContext, hasBt)
        list.addView(card().apply {
            addView(sectionTitle("Bluetooth"))
            addView(TextView(this@MainActivity).apply {
                text = when {
                    !bt.supported -> "非対応"
                    !bt.enabled -> "OFF"
                    bt.connected.isNotEmpty() -> "接続中: ${bt.connected.size} 台"
                    else -> "ON（接続中の機器なし）"
                }
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (bt.connected.isNotEmpty()) greenColor else subColor)
                setPadding(0, dp(6), 0, dp(4))
            })
            bt.connected.forEach { d ->
                addView(TextView(this@MainActivity).apply {
                    text = "● ${d.name}（${d.type}）"
                    textSize = 14f
                    setTextColor(textColor)
                })
            }
            if (bt.bonded.isNotEmpty()) {
                addView(TextView(this@MainActivity).apply {
                    text = "ペアリング済み（${bt.bonded.size} 台）:\n" +
                        bt.bonded.joinToString("\n") { "・${it.name}（${it.type}）" }
                    textSize = 12f
                    setTextColor(subColor)
                    setPadding(0, dp(6), 0, 0)
                })
            }
            addView(TextView(this@MainActivity).apply {
                text = bt.note
                textSize = 12f
                setTextColor(if (bt.enabled) subColor else yellowColor)
                setPadding(0, dp(6), 0, 0)
            })
            if (Build.VERSION.SDK_INT >= 31 && !hasBt) {
                addView(Button(this@MainActivity).apply {
                    text = "「近くのデバイス」を許可"
                    textSize = 13f
                    isAllCaps = false
                    setTextColor(Color.BLACK)
                    setBackgroundColor(yellowColor)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
                    ).apply { topMargin = dp(8) }
                    setOnClickListener { requestStatusPermissions() }
                })
            }
        })

        // ---------- 外部デバイス ----------
        val usb = DeviceStatus.usbDevices(applicationContext)
        list.addView(card().apply {
            addView(sectionTitle("接続中の外部デバイス"))
            if (usb.isEmpty()) {
                addView(TextView(this@MainActivity).apply {
                    text = "接続されていません"
                    textSize = 14f
                    setTextColor(subColor)
                    setPadding(0, dp(6), 0, 0)
                })
            } else {
                usb.forEach { d ->
                    addView(TextView(this@MainActivity).apply {
                        text = "● ${d.name}"
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(textColor)
                        setPadding(0, dp(8), 0, 0)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = d.detail
                        textSize = 12f
                        setTextColor(subColor)
                    })
                }
                addView(TextView(this@MainActivity).apply {
                    text = "身に覚えのない機器が接続されている場合は、すぐに取り外してください。"
                    textSize = 12f
                    setTextColor(yellowColor)
                    setPadding(0, dp(8), 0, 0)
                })
            }
        })

        // ---------- 起動中のアプリ ----------
        val (apps, appNote) = DeviceStatus.runningApps(applicationContext)
        list.addView(card().apply {
            addView(sectionTitle("起動中のアプリ"))
            if (apps.isEmpty()) {
                addView(TextView(this@MainActivity).apply {
                    text = "取得できませんでした"
                    textSize = 14f
                    setTextColor(subColor)
                    setPadding(0, dp(6), 0, 0)
                })
            } else {
                apps.take(40).forEach { a ->
                    addView(TextView(this@MainActivity).apply {
                        text = "・${a.label}"
                        textSize = 14f
                        setTextColor(if (a.isForeground) greenColor else textColor)
                        setPadding(0, dp(4), 0, 0)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = "　${a.importance}　${a.packageName}"
                        textSize = 11f
                        setTextColor(subColor)
                    })
                }
            }
            addView(TextView(this@MainActivity).apply {
                text = appNote
                textSize = 12f
                setTextColor(yellowColor)
                setPadding(0, dp(8), 0, dp(4))
            })
            addView(Button(this@MainActivity).apply {
                text = "アプリの設定画面を開く"
                textSize = 13f
                isAllCaps = false
                setTextColor(textColor)
                setBackgroundColor(bgColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
                ).apply { topMargin = dp(4) }
                setOnClickListener {
                    openSafely(Intent(Settings.ACTION_APPLICATION_SETTINGS))
                }
            })
        })

        return ScrollView(this).apply { addView(list) }
    }

    private fun sectionTitle(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 15f
        setTypeface(null, Typeface.BOLD)
        setTextColor(accentColor)
    }

    private fun requestStatusPermissions() {
        val perms = mutableListOf<String>()
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            perms.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission("android.permission.BLUETOOTH_CONNECT")
            != PackageManager.PERMISSION_GRANTED) {
            perms.add("android.permission.BLUETOOTH_CONNECT")
        }
        if (perms.isEmpty()) {
            renderContent()
        } else {
            requestPermissions(perms.toTypedArray(), REQ_STATUS_PERM)
        }
    }

    private fun openSystemUpdate() {
        val candidates = listOf(
            Intent("android.settings.SYSTEM_UPDATE_SETTINGS"),
            Intent("android.settings.DEVICE_INFO_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (i in candidates) {
            try {
                startActivity(i)
                return
            } catch (e: Exception) { }
        }
        Toast.makeText(this, "設定アプリから「システム更新」を開いてください",
            Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STATUS_PERM) renderContent()
    }

    // =========================================================
    // 状態 > バッテリー
    // =========================================================
    private fun buildBatteryView(): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }
        val b = BatteryHealth.read(applicationContext)

        list.addView(card().apply {
            addView(sectionTitle("バッテリーの劣化度"))
            if (b.available) {
                val c = when {
                    b.healthPercent >= 90 -> greenColor
                    b.healthPercent >= 80 -> greenColor
                    b.healthPercent >= 70 -> yellowColor
                    else -> redColor
                }
                addView(TextView(this@MainActivity).apply {
                    text = "劣化 ${b.wearPercent}%"
                    textSize = 26f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(c)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, 0)
                })
                addView(TextView(this@MainActivity).apply {
                    text = "健康度 ${b.healthPercent}%（新品時を100%とした推定）"
                    textSize = 13f
                    setTextColor(c)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(2), 0, dp(8))
                })
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(14))
                    addView(View(this@MainActivity).apply {
                        setBackgroundColor(c)
                        layoutParams = LinearLayout.LayoutParams(
                            0, dp(14), b.healthPercent.coerceIn(1, 100).toFloat())
                    })
                    addView(View(this@MainActivity).apply {
                        setBackgroundColor(bgColor)
                        layoutParams = LinearLayout.LayoutParams(
                            0, dp(14), (100 - b.healthPercent).coerceIn(0, 99).toFloat())
                    })
                })
                addView(TextView(this@MainActivity).apply {
                    text = "設計容量 ${b.designCapacityMah} mAh → 現在 約${b.currentCapacityMah} mAh"
                    textSize = 13f
                    setTextColor(textColor)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, 0)
                })
            } else {
                addView(TextView(this@MainActivity).apply {
                    text = "算出できませんでした"
                    textSize = 17f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(yellowColor)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, dp(4))
                })
            }
            addView(TextView(this@MainActivity).apply {
                text = b.note
                textSize = 12f
                setTextColor(subColor)
                setPadding(0, dp(10), 0, 0)
            })
        })

        list.addView(card().apply {
            addView(sectionTitle("現在の状態"))
            addView(TextView(this@MainActivity).apply {
                text = listOf(
                    if (b.level >= 0) "残量: ${b.level}%" else "",
                    "充電状態: ${b.statusText}",
                    "OS報告の状態: ${b.healthText}",
                    if (b.temperatureC > 0)
                        String.format(Locale.US, "温度: %.1f ℃", b.temperatureC) else "",
                    if (b.voltageV > 0)
                        String.format(Locale.US, "電圧: %.2f V", b.voltageV) else "",
                    "種類: ${b.technology}",
                    if (b.cycleCount >= 0) "充放電回数: ${b.cycleCount} 回" else ""
                ).filter { it.isNotEmpty() }.joinToString("\n")
                textSize = 14f
                setTextColor(textColor)
                setPadding(0, dp(6), 0, 0)
            })
        })

        list.addView(card().apply {
            addView(sectionTitle("評価と対策"))
            addView(TextView(this@MainActivity).apply {
                text = b.advice
                textSize = 13f
                setTextColor(textColor)
                setPadding(0, dp(6), 0, 0)
            })
        })

        list.addView(Button(this).apply {
            text = "バッテリーの設定画面を開く"
            textSize = 13f
            isAllCaps = false
            setTextColor(textColor)
            setBackgroundColor(cardColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
            ).apply { topMargin = dp(10) }
            setOnClickListener {
                openSafely(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            }
        })

        list.addView(Button(this).apply {
            text = "再測定"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.BLACK)
            setBackgroundColor(accentColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
            ).apply { topMargin = dp(8) }
            setOnClickListener { renderContent() }
        })

        return ScrollView(this).apply { addView(list) }
    }

    // =========================================================
    // 状態 > フォルダ集計（ダイジェスト）
    // =========================================================
    private val REQ_DIGEST_TREE = 3002
    private var digestBusy = false
    private var digestResult: FolderDigest.Snapshot? = null
    private var digestTargetId: String? = null
    private var digestDiff: FolderDigest.Diff? = null

    private fun buildDigestView(): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }

        list.addView(card().apply {
            addView(sectionTitle("フォルダ集計（ダイジェスト）"))
            addView(TextView(this@MainActivity).apply {
                text = "選んだフォルダの中身を1画面で集計し、日付つきで記録します。" +
                    "前回との差を見ることで「知らないうちに増えた」ことに気付けます。\n\n" +
                    "手順1. 「フォルダを選んで集計」を押す\n" +
                    "手順2. 対象フォルダを選ぶ（ダウンロード等）\n" +
                    "手順3. 集計結果と前回からの増減を確認\n" +
                    "手順4. ときどき同じフォルダで再集計する"
                textSize = 13f
                setTextColor(textColor)
                setPadding(0, dp(6), 0, 0)
            })
        })

        list.addView(Button(this).apply {
            text = if (digestBusy) "集計中…" else "フォルダを選んで集計"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.BLACK)
            setBackgroundColor(accentColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
            ).apply { topMargin = dp(10) }
            setOnClickListener {
                if (!digestBusy) {
                    try {
                        startActivityForResult(
                            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, REQ_DIGEST_TREE)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity,
                            "フォルダ選択画面を開けませんでした", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        if (digestBusy) {
            list.addView(card().apply {
                addView(TextView(this@MainActivity).apply {
                    text = "集計しています…"
                    textSize = 14f
                    setTextColor(textColor)
                })
            })
        }

        // ---- 今回の集計結果 ----
        val r = digestResult
        if (r != null) {
            list.addView(card().apply {
                addView(sectionTitle("集計結果"))
                addView(TextView(this@MainActivity).apply {
                    text = "${r.totalFiles} 個"
                    textSize = 28f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(textColor)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(6), 0, 0)
                })
                addView(TextView(this@MainActivity).apply {
                    text = "ファイル総数（フォルダ ${r.totalDirs} 個）\n" +
                        SimpleDateFormat("yyyy/M/d HH:mm", Locale.JAPAN)
                            .format(Date(r.time)) + " 時点" +
                        (if (r.truncated) "\n※件数が多いため途中で打ち切りました" else "")
                    textSize = 12f
                    setTextColor(subColor)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(4), 0, dp(8))
                })
                r.byCategory.entries.sortedByDescending { it.value }.forEach { (cat, n) ->
                    addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, dp(3), 0, dp(3))
                        addView(TextView(this@MainActivity).apply {
                            text = if (cat == "危険") "● $cat" else "・$cat"
                            textSize = 14f
                            setTextColor(if (cat == "危険") redColor else textColor)
                            layoutParams = LinearLayout.LayoutParams(0,
                                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(TextView(this@MainActivity).apply {
                            text = "$n 個"
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(if (cat == "危険") redColor else textColor)
                        })
                    })
                }
            })

            // ---- 前回との差分 ----
            val d = digestDiff
            if (d != null) {
                list.addView(card().apply {
                    addView(sectionTitle("前回からの変化"))
                    addView(TextView(this@MainActivity).apply {
                        text = "前回: " + SimpleDateFormat("yyyy/M/d HH:mm", Locale.JAPAN)
                            .format(Date(d.prevTime))
                        textSize = 12f
                        setTextColor(subColor)
                        setPadding(0, dp(4), 0, dp(4))
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = when {
                            d.fileDelta > 0 -> "ファイルが ${d.fileDelta} 個 増えました"
                            d.fileDelta < 0 -> "ファイルが ${-d.fileDelta} 個 減りました"
                            else -> "ファイル数に変化はありません"
                        }
                        textSize = 16f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(if (d.fileDelta > 0) yellowColor else textColor)
                    })
                    if (d.riskyDelta > 0) {
                        addView(TextView(this@MainActivity).apply {
                            text = "うち危険な拡張子が ${d.riskyDelta} 個 増えています。" +
                                "心当たりがない場合は下の一覧を確認してください。"
                            textSize = 13f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(redColor)
                            setPadding(0, dp(6), 0, 0)
                        })
                    }
                    if (d.categoryDelta.isNotEmpty()) {
                        addView(TextView(this@MainActivity).apply {
                            text = d.categoryDelta.entries
                                .sortedByDescending { kotlin.math.abs(it.value) }
                                .joinToString("\n") { (c, v) ->
                                    "・$c: ${if (v > 0) "+$v" else "$v"}"
                                }
                            textSize = 13f
                            setTextColor(textColor)
                            setPadding(0, dp(6), 0, 0)
                        })
                    }
                })
            }

            // ---- 危険な拡張子の一覧 ----
            if (r.riskyFiles.isNotEmpty()) {
                list.addView(card().apply {
                    addView(TextView(this@MainActivity).apply {
                        text = "注意が必要なファイル（${r.riskyFiles.size} 件）"
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(redColor)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = "身に覚えのないものは開かないでください。" +
                            "詳しい検査は診断タブ→その他→外部メディア検査で行えます。"
                        textSize = 12f
                        setTextColor(subColor)
                        setPadding(0, dp(4), 0, 0)
                    })
                })
                r.riskyFiles.take(50).forEach { f ->
                    list.addView(card().apply {
                        addView(TextView(this@MainActivity).apply {
                            text = f.name
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(redColor)
                        })
                        addView(TextView(this@MainActivity).apply {
                            text = "${f.path}\n${f.reason}"
                            textSize = 12f
                            setTextColor(subColor)
                            setPadding(0, dp(2), 0, 0)
                        })
                    })
                }
            }
        }

        // ---- 記録済みフォルダと履歴 ----
        val targets = FolderDigest.targets(applicationContext)
        if (targets.isNotEmpty()) {
            list.addView(card().apply {
                addView(sectionTitle("記録している集計"))
            })
            targets.forEach { t ->
                val hist = FolderDigest.history(applicationContext, t.id)
                list.addView(card().apply {
                    addView(TextView(this@MainActivity).apply {
                        text = t.name
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(textColor)
                    })
                    if (hist.isEmpty()) {
                        addView(TextView(this@MainActivity).apply {
                            text = "記録なし"
                            textSize = 12f
                            setTextColor(subColor)
                        })
                    } else {
                        addView(TextView(this@MainActivity).apply {
                            text = hist.reversed().take(10).joinToString("\n") { s ->
                                SimpleDateFormat("yyyy/M/d HH:mm", Locale.JAPAN)
                                    .format(Date(s.time)) +
                                "　${s.totalFiles} 個" +
                                (if (s.riskyCount > 0) "（危険 ${s.riskyCount}）" else "")
                            }
                            textSize = 12f
                            setTextColor(textColor)
                            setPadding(0, dp(6), 0, 0)
                        })
                    }
                    addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(8) }
                        addView(Button(this@MainActivity).apply {
                            text = "再集計"
                            textSize = 11f
                            isAllCaps = false
                            setPadding(0, 0, 0, 0)
                            setTextColor(Color.BLACK)
                            setBackgroundColor(accentColor)
                            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
                                .apply { setMargins(dp(2), 0, dp(2), 0) }
                            setOnClickListener { runDigest(Uri.parse(t.uri), t.id, t.name) }
                        })
                        addView(Button(this@MainActivity).apply {
                            text = "削除"
                            textSize = 11f
                            isAllCaps = false
                            setPadding(0, 0, 0, 0)
                            setTextColor(textColor)
                            setBackgroundColor(bgColor)
                            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
                                .apply { setMargins(dp(2), 0, dp(2), 0) }
                            setOnClickListener {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("確認")
                                    .setMessage("「${t.name}」の記録を削除しますか？")
                                    .setPositiveButton("削除") { _, _ ->
                                        FolderDigest.removeTarget(applicationContext, t.id)
                                        if (digestTargetId == t.id) {
                                            digestResult = null; digestDiff = null
                                        }
                                        renderContent()
                                    }
                                    .setNegativeButton("やめる", null)
                                    .show()
                            }
                        })
                    })
                })
            }
        }

        return ScrollView(this).apply { addView(list) }
    }

    private fun runDigest(uri: Uri, targetId: String, name: String) {
        digestBusy = true
        digestResult = null
        digestDiff = null
        renderContent()
        Thread {
            val snap = try {
                FolderDigest.scan(applicationContext, uri)
            } catch (e: Exception) {
                FolderDigest.Snapshot(System.currentTimeMillis(), 0, 0,
                    emptyMap(), 0, false, emptyList())
            }
            val prev = FolderDigest.history(applicationContext, targetId).lastOrNull()
            val d = FolderDigest.diff(prev, snap)
            FolderDigest.record(applicationContext, targetId, snap)
            runOnUiThread {
                digestBusy = false
                digestResult = snap
                digestDiff = d
                digestTargetId = targetId
                renderContent()
            }
        }.start()
    }

    // =========================================================
    // 通信 > ツール起動カード
    // =========================================================
    private fun buildToolLauncher(page: Int, title: String, desc: String): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }
        list.addView(card().apply {
            addView(sectionTitle(title))
            addView(TextView(this@MainActivity).apply {
                text = desc
                textSize = 13f
                setTextColor(textColor)
                setPadding(0, dp(6), 0, 0)
            })
            addView(Button(this@MainActivity).apply {
                text = "開く"
                textSize = 15f
                isAllCaps = false
                setTextColor(Color.BLACK)
                setBackgroundColor(accentColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
                ).apply { topMargin = dp(10) }
                setOnClickListener {
                    try {
                        startActivity(Intent(this@MainActivity, ToolsActivity::class.java)
                            .putExtra(ToolsActivity.EXTRA_PAGE, page))
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity,
                            "画面を開けませんでした", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        })
        return ScrollView(this).apply { addView(list) }
    }

    // =========================================================
    // 診断 > その他
    // =========================================================
    private fun buildOtherView(): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }

        val tools = listOf(
            Triple(0, "外部メディア検査",
                "USBメモリやSDカードの中身を調べ、実行ファイル・二重拡張子・" +
                "ファイル名偽装・拡張子と中身の不一致を指摘します。"),
            Triple(3, "機器バージョン台帳",
                "ルーターやPCのバージョンを記録し、メーカーの更新情報ページの" +
                "変化を検知して知らせます。")
        )

        tools.forEach { (idx, title, desc) ->
            list.addView(card().apply {
                addView(sectionTitle(title))
                addView(TextView(this@MainActivity).apply {
                    text = desc
                    textSize = 13f
                    setTextColor(textColor)
                    setPadding(0, dp(6), 0, dp(4))
                })
                addView(Button(this@MainActivity).apply {
                    text = "開く"
                    textSize = 14f
                    isAllCaps = false
                    setTextColor(Color.BLACK)
                    setBackgroundColor(accentColor)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
                    ).apply { topMargin = dp(6) }
                    setOnClickListener {
                        try {
                            startActivity(Intent(this@MainActivity, ToolsActivity::class.java)
                                .putExtra(ToolsActivity.EXTRA_PAGE, idx))
                        } catch (e: Exception) { }
                    }
                })
            })
        }

        // 関所の案内
        list.addView(card().apply {
            addView(sectionTitle("関所（漏洩ガード）"))
            addView(TextView(this@MainActivity).apply {
                text = "他のアプリでテキストや画像を共有するとき、共有先の一覧から" +
                    "『守りのDX関所』を選ぶと、転送前に内容を検査します。\n\n" +
                    "検査項目: マイナンバー（検査番号まで照合）、クレジットカード番号、" +
                    "機密キーワード、画像の位置情報、リンクの安全性。\n\n" +
                    "この機能は他のアプリの共有メニューから使うため、" +
                    "ここには操作ボタンがありません。使い方は説明書タブをご覧ください。"
                textSize = 13f
                setTextColor(textColor)
                setPadding(0, dp(6), 0, 0)
            })
        })

        return ScrollView(this).apply { addView(list) }
    }

    // =========================================================
    // 診断 > APK掃除
    // =========================================================
    private val REQ_APK_TREE = 3003
    private var apkBusy = false
    private var apkResult: ApkCleaner.ScanResult? = null
    private var apkTreeUri: Uri? = null
    private var apkMessage: String? = null

    private fun buildApkCleanView(): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }

        list.addView(card().apply {
            addView(sectionTitle("APKファイルの掃除"))
            addView(TextView(this@MainActivity).apply {
                text = "端末に残ったAPK（アプリのインストール用ファイル）を探して一括削除します。\n\n" +
                    "APKが端末に残っていると、誤って実行して不正アプリを入れてしまう危険があります。" +
                    "また古いAPKは既知の弱点を含んだままのことが多く、置いておく利点はほぼありません。\n\n" +
                    "手順1. 「フォルダを選んでAPKを探す」を押す\n" +
                    "手順2. ダウンロードフォルダなどを選ぶ（内部ストレージ全体でも可）\n" +
                    "手順3. 見つかった一覧を確認\n" +
                    "手順4. 「すべて削除」を押す"
                textSize = 13f
                setTextColor(textColor)
                setPadding(0, dp(6), 0, 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = "※Androidの仕様上、削除できるのは利用者が選んだフォルダの中だけです。" +
                    "端末全体を対象にしたい場合は、フォルダ選択画面で内部ストレージの最上位を選んでください。"
                textSize = 12f
                setTextColor(yellowColor)
                setPadding(0, dp(8), 0, 0)
            })
        })

        list.addView(Button(this).apply {
            text = if (apkBusy) "処理中…" else "フォルダを選んでAPKを探す"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.BLACK)
            setBackgroundColor(accentColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
            ).apply { topMargin = dp(10) }
            setOnClickListener {
                if (!apkBusy) {
                    try {
                        startActivityForResult(
                            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            }, REQ_APK_TREE)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity,
                            "フォルダ選択画面を開けませんでした", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        val msg = apkMessage
        if (msg != null) {
            list.addView(card().apply {
                addView(TextView(this@MainActivity).apply {
                    text = msg
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(greenColor)
                })
            })
        }

        if (apkBusy) {
            list.addView(card().apply {
                addView(TextView(this@MainActivity).apply {
                    text = "検索または削除を実行しています…"
                    textSize = 14f
                    setTextColor(textColor)
                })
            })
        }

        val r = apkResult
        if (r != null && !apkBusy) {
            list.addView(card().apply {
                addView(TextView(this@MainActivity).apply {
                    text = if (r.files.isEmpty()) "✓ APKファイルは見つかりませんでした"
                    else "APKファイル ${r.files.size} 件（合計 " +
                        ApkCleaner.formatBytes(ApkCleaner.totalSize(r.files)) + "）"
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (r.files.isEmpty()) greenColor else yellowColor)
                })
                addView(TextView(this@MainActivity).apply {
                    text = "走査したファイル ${r.scannedFiles} 件" +
                        (if (r.truncated) "\n※件数が多いため途中で打ち切りました" else "")
                    textSize = 12f
                    setTextColor(subColor)
                    setPadding(0, dp(4), 0, 0)
                })
            })

            if (r.files.isNotEmpty()) {
                list.addView(Button(this).apply {
                    text = "すべて削除（${r.files.size} 件）"
                    textSize = 15f
                    isAllCaps = false
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.BLACK)
                    setBackgroundColor(redColor)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(50)
                    ).apply { topMargin = dp(10) }
                    setOnClickListener { confirmDeleteApks(r) }
                })

                r.files.take(100).forEach { f ->
                    list.addView(card().apply {
                        addView(TextView(this@MainActivity).apply {
                            text = f.name
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(textColor)
                        })
                        addView(TextView(this@MainActivity).apply {
                            text = "${f.path}\n" +
                                ApkCleaner.formatBytes(f.sizeBytes) +
                                (if (f.lastModified > 0)
                                    "　" + SimpleDateFormat("yyyy/M/d", Locale.JAPAN)
                                        .format(Date(f.lastModified)) else "")
                            textSize = 11f
                            setTextColor(subColor)
                            setPadding(0, dp(2), 0, 0)
                        })
                    })
                }
            }
        }

        // ---- 提供元不明アプリの許可 ----
        list.addView(card().apply {
            addView(sectionTitle("提供元不明アプリのインストール許可"))
            addView(TextView(this@MainActivity).apply {
                text = "この許可が付いているアプリは、ストアを経由せずに他のアプリを導入できます。" +
                    "不正アプリの主要な侵入口なので、必要のないものは無効にしてください。"
                textSize = 13f
                setTextColor(textColor)
                setPadding(0, dp(6), 0, dp(4))
            })
            addView(TextView(this@MainActivity).apply {
                text = "※Androidの仕様上、この設定をアプリから直接変更することはできません。" +
                    "下のボタンから設定画面を開いて切り替えてください。"
                textSize = 12f
                setTextColor(yellowColor)
            })
            addView(Button(this@MainActivity).apply {
                text = "インストール許可の設定を開く"
                textSize = 14f
                isAllCaps = false
                setTextColor(Color.BLACK)
                setBackgroundColor(accentColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
                ).apply { topMargin = dp(8) }
                setOnClickListener {
                    openSafely(Intent(
                        if (Build.VERSION.SDK_INT >= 26)
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                        else Settings.ACTION_SECURITY_SETTINGS))
                }
            })
        })

        val installers = ApkCleaner.installers(applicationContext)
        val grantedOnes = installers.filter { it.granted }
        list.addView(card().apply {
            addView(TextView(this@MainActivity).apply {
                text = if (grantedOnes.isEmpty())
                    "✓ 許可されているアプリはありません"
                else "許可されているアプリ: ${grantedOnes.size} 件"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (grantedOnes.isEmpty()) greenColor else redColor)
            })
        })

        grantedOnes.forEach { ins ->
            val c = when (ins.risk) {
                2 -> redColor
                1 -> yellowColor
                else -> subColor
            }
            list.addView(card().apply {
                addView(TextView(this@MainActivity).apply {
                    text = ins.label
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(c)
                })
                addView(TextView(this@MainActivity).apply {
                    text = ins.packageName
                    textSize = 11f
                    setTextColor(subColor)
                    setPadding(0, dp(2), 0, dp(4))
                })
                addView(TextView(this@MainActivity).apply {
                    text = ins.reason
                    textSize = 12f
                    setTextColor(textColor)
                })
                addView(Button(this@MainActivity).apply {
                    text = "このアプリの設定を開く"
                    textSize = 13f
                    isAllCaps = false
                    setTextColor(Color.BLACK)
                    setBackgroundColor(c)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(42)
                    ).apply { topMargin = dp(8) }
                    setOnClickListener {
                        try {
                            startActivity(Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${ins.packageName}")))
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity,
                                "設定画面を開けませんでした", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            })
        }

        return ScrollView(this).apply { addView(list) }
    }

    private fun confirmDeleteApks(r: ApkCleaner.ScanResult) {
        val uri = apkTreeUri ?: return
        AlertDialog.Builder(this)
            .setTitle("APKを削除します")
            .setMessage("${r.files.size} 件（合計 " +
                ApkCleaner.formatBytes(ApkCleaner.totalSize(r.files)) +
                "）を削除します。\n\nこの操作は取り消せません。よろしいですか？")
            .setPositiveButton("削除する") { _, _ ->
                apkBusy = true
                apkMessage = null
                renderContent()
                Thread {
                    val (ok, ng) = ApkCleaner.deleteAll(applicationContext, uri, r.files)
                    val rescan = try {
                        ApkCleaner.scan(applicationContext, uri)
                    } catch (e: Exception) { null }
                    runOnUiThread {
                        apkBusy = false
                        apkResult = rescan
                        apkMessage = if (ng == 0)
                            "$ok 件のAPKを削除しました。"
                        else "$ok 件を削除しました。$ng 件は削除できませんでした" +
                            "（読み取り専用の場所や、権限のないフォルダの可能性があります）。"
                        renderContent()
                    }
                }.start()
            }
            .setNegativeButton("やめる", null)
            .show()
    }

    // =========================================================
    // 診断 > 権限監査（ユーザー補助）
    // =========================================================
    private var a11yShowAvailable = false

    private fun buildA11yAuditView(): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }

        val rep = AccessibilityAudit.run(applicationContext)

        list.addView(card().apply {
            addView(sectionTitle("ユーザー補助の権限監査"))
            addView(TextView(this@MainActivity).apply {
                text = "ユーザー補助（アクセシビリティ）は、画面の内容をすべて読み取り、" +
                    "利用者の代わりに操作できる最も強力な権限です。" +
                    "不正アプリが真っ先に狙う設定でもあります。"
                textSize = 13f
                setTextColor(textColor)
                setPadding(0, dp(6), 0, 0)
            })
        })

        val gc = when (rep.globalRisk) {
            AccessibilityAudit.RISK_CRITICAL -> redColor
            AccessibilityAudit.RISK_HIGH -> redColor
            AccessibilityAudit.RISK_MID -> yellowColor
            else -> greenColor
        }
        list.addView(card().apply {
            addView(TextView(this@MainActivity).apply {
                text = if (rep.enabledEntries.isEmpty())
                    "✓ 有効なアプリなし" else "有効: ${rep.enabledEntries.size} 件"
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(gc)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = rep.summary
                textSize = 13f
                setTextColor(textColor)
                setPadding(0, dp(8), 0, 0)
            })
        })

        list.addView(Button(this).apply {
            text = "ユーザー補助の設定を開く"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.BLACK)
            setBackgroundColor(accentColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
            ).apply { topMargin = dp(10) }
            setOnClickListener {
                openSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        // ---- 有効なもの ----
        rep.enabledEntries.forEach { e -> list.addView(a11yCard(e)) }

        // ---- 未使用だが導入済みのもの ----
        if (rep.availableEntries.isNotEmpty()) {
            list.addView(Button(this).apply {
                text = if (a11yShowAvailable)
                    "この機能を使えるアプリを隠す（${rep.availableEntries.size} 件）"
                else "この機能を使えるアプリも見る（${rep.availableEntries.size} 件）"
                textSize = 13f
                isAllCaps = false
                setTextColor(textColor)
                setBackgroundColor(cardColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
                ).apply { topMargin = dp(12) }
                setOnClickListener {
                    a11yShowAvailable = !a11yShowAvailable
                    renderContent()
                }
            })
            if (a11yShowAvailable) {
                list.addView(card().apply {
                    addView(TextView(this@MainActivity).apply {
                        text = "以下は現在は有効になっていませんが、" +
                            "ユーザー補助を要求できるアプリです。" +
                            "身に覚えのないアプリが並んでいる場合は、そのアプリ自体を確認してください。"
                        textSize = 12f
                        setTextColor(subColor)
                    })
                })
                rep.availableEntries.forEach { e -> list.addView(a11yCard(e)) }
            }
        }

        list.addView(card().apply {
            addView(TextView(this@MainActivity).apply {
                text = "【安全のための原則】\n" +
                    "・支援機能（画面読み上げ等）とパスワード管理以外で、" +
                    "この権限を求めるアプリは基本的に不要です\n" +
                    "・「動作に必要」と説明されても、安易に許可しないでください\n" +
                    "・使い終わったらOFFに戻す運用が最も安全です"
                textSize = 12f
                setTextColor(subColor)
            })
        })

        return ScrollView(this).apply { addView(list) }
    }

    private fun a11yCard(e: AccessibilityAudit.Entry): View {
        val c = when (e.risk) {
            AccessibilityAudit.RISK_CRITICAL -> redColor
            AccessibilityAudit.RISK_HIGH -> redColor
            AccessibilityAudit.RISK_MID -> yellowColor
            else -> if (e.enabled) greenColor else subColor
        }
        return card().apply {
            if (e.risk >= AccessibilityAudit.RISK_HIGH && e.enabled) {
                setBackgroundColor(Color.parseColor("#3A1E1E"))
            }
            addView(TextView(this@MainActivity).apply {
                text = "[リスク ${AccessibilityAudit.riskLabel(e.risk)}]" +
                    (if (e.enabled) " 有効" else " 未使用")
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(c)
            })
            addView(TextView(this@MainActivity).apply {
                text = e.label
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(textColor)
                setPadding(0, dp(2), 0, dp(2))
            })
            addView(TextView(this@MainActivity).apply {
                text = "${e.packageName}\n入手元: ${e.installerName}"
                textSize = 11f
                setTextColor(subColor)
                setPadding(0, 0, 0, dp(6))
            })
            addView(TextView(this@MainActivity).apply {
                text = "できること:\n" + e.capabilities.joinToString("\n") { "・$it" }
                textSize = 12f
                setTextColor(textColor)
            })
            addView(TextView(this@MainActivity).apply {
                text = "\n判定理由:\n" + e.reasons.joinToString("\n") { "・$it" }
                textSize = 12f
                setTextColor(subColor)
            })
            if (e.description.isNotBlank()) {
                addView(TextView(this@MainActivity).apply {
                    text = "\nアプリの説明:\n${e.description}"
                    textSize = 11f
                    setTextColor(subColor)
                })
            }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
                addView(Button(this@MainActivity).apply {
                    text = "設定で無効化"
                    textSize = 11f
                    isAllCaps = false
                    setPadding(0, 0, 0, 0)
                    setTextColor(Color.BLACK)
                    setBackgroundColor(c)
                    layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
                        .apply { setMargins(dp(2), 0, dp(2), 0) }
                    setOnClickListener {
                        openSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                })
                addView(Button(this@MainActivity).apply {
                    text = "アプリ情報"
                    textSize = 11f
                    isAllCaps = false
                    setPadding(0, 0, 0, 0)
                    setTextColor(textColor)
                    setBackgroundColor(bgColor)
                    layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
                        .apply { setMargins(dp(2), 0, dp(2), 0) }
                    setOnClickListener {
                        try {
                            startActivity(Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${e.packageName}")))
                        } catch (ex: Exception) { }
                    }
                })
            })
        }
    }

    // ===== VPN制御 =====
    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // 初回はシステムのVPN許可ダイアログを表示
            startActivityForResult(intent, VPN_REQUEST)
        } else {
            onActivityResult(VPN_REQUEST, Activity.RESULT_OK, null)
        }
    }

    private fun stopVpn() {
        val i = Intent(this, DnsMonitorService::class.java).apply {
            action = DnsMonitorService.ACTION_STOP
        }
        startService(i)
        // UIを少し遅らせて更新
        contentArea.postDelayed({ if (currentTab == 1 && commsSub == 0) renderContent() }, 300)
    }

    private var currentTab = 0
    private var statusSub = 0
    private var commsSub = 0
    private var diagSub = 0
    private lateinit var subTabArea: LinearLayout

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_DIGEST_TREE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { }
            val name = uri.lastPathSegment?.substringAfterLast(':')
                ?.ifEmpty { "選択フォルダ" } ?: "選択フォルダ"
            // 同じフォルダなら既存の記録に追記する
            val existing = FolderDigest.targets(applicationContext)
                .firstOrNull { it.uri == uri.toString() }
            val target = existing
                ?: FolderDigest.addTarget(applicationContext, name, uri.toString())
            runDigest(uri, target.id, target.name)
            return
        }
        if (requestCode == REQ_APK_TREE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (e: Exception) { }
            apkTreeUri = uri
            apkBusy = true
            apkResult = null
            apkMessage = null
            renderContent()
            Thread {
                val res = try {
                    ApkCleaner.scan(applicationContext, uri)
                } catch (e: Exception) {
                    ApkCleaner.ScanResult(emptyList(), 0, false)
                }
                runOnUiThread {
                    apkBusy = false
                    apkResult = res
                    renderContent()
                }
            }.start()
            return
        }
        if (requestCode == VPN_REQUEST && resultCode == Activity.RESULT_OK) {
            val i = Intent(this, DnsMonitorService::class.java).apply {
                action = DnsMonitorService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            contentArea.postDelayed({ if (currentTab == 1 && commsSub == 0) renderContent() }, 500)
        } else if (requestCode == VPN_REQUEST) {
            AlertDialog.Builder(this)
                .setTitle("VPNが許可されませんでした")
                .setMessage("通信記録にはVPNの許可が必要です。" +
                    "記録専用で外部送信は行いません（説明書タブ参照）。")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // ===== UI部品 =====
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(cardColor)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(8), 0, 0) }
    }

    private fun row(label: String, value: String, color: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(this@MainActivity).apply {
                text = "● $label"
                textSize = 14f
                setTextColor(color)
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = value
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(textColor)
            })
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
