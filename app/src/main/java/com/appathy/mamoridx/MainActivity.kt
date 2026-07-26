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

        // ===== タブ =====
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(8))
        }
        val tabNames = listOf("棚卸し", "端末診断", "通信ログ", "緊急対応", "説明書")
        tabButtons = tabNames.mapIndexed { index, name ->
            Button(this).apply {
                text = name
                textSize = 11f
                isAllCaps = false
                setPadding(0, 0, 0, 0)
                setTextColor(textColor)
                setBackgroundColor(cardColor)
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                setOnClickListener { showTab(index) }
            }
        }
        tabButtons.forEach { tabRow.addView(it) }
        root.addView(tabRow)

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

    private fun showTab(index: Int) {
        currentTab = index
        tabButtons.forEachIndexed { i, b ->
            b.setBackgroundColor(if (i == index) accentColor else cardColor)
            b.setTextColor(if (i == index) Color.BLACK else textColor)
        }
        contentArea.removeAllViews()
        when (index) {
            0 -> contentArea.addView(buildInventoryView())
            1 -> contentArea.addView(buildPostureView())
            2 -> contentArea.addView(buildCommsView())
            3 -> contentArea.addView(buildEmergencyView())
            4 -> contentArea.addView(buildManualView())
        }
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
        val pageNames = listOf("概要", "棚卸し", "診断", "通信", "緊急", "関所")
        val selector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(12), dp(8))
        }
        pageNames.forEachIndexed { i, name ->
            selector.addView(Button(this).apply {
                text = name
                textSize = 11f
                isAllCaps = false
                setPadding(0, 0, 0, 0)
                val sel = manualPage == i
                setTextColor(if (sel) Color.BLACK else subColor)
                setBackgroundColor(if (sel) accentColor else cardColor)
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    manualPage = i
                    showTab(4)
                }
            })
        }
        outer.addView(selector)

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

                section("4つの機能と使う順番",
                    "はじめての方は次の順で使うのがおすすめです。\n\n" +
                    "手順1. 「端末診断」で土台（端末自体の防御力）を確認\n" +
                    "手順2. 「アプリ棚卸し」で入っているアプリを3分類\n" +
                    "手順3. 「通信ログ」でアプリの通信先（SaaS）を棚卸し\n" +
                    "手順4. 日常の共有時は「関所」を通して漏洩を防止\n\n" +
                    "上の「棚卸し/診断/通信/関所」ボタンから各機能の詳しい説明書を開けます。")

                section("このアプリが集めない情報",
                    "診断結果・DNSログ・ポリシーは、すべて端末内にのみ保存され、" +
                    "外部へは一切送信しません。VPNは端末内で完結し、外部のVPNサーバーには接続しません。" +
                    "共有内容の検査も端末内で完結します。")

                section("Appathy",
                    "Less Motivation, More Automation.\n" +
                    "本アプリはスマホのみ（Termux + GitHub Actions）で開発されています。")
            }

            // ---------- アプリ棚卸し ----------
            1 -> {
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
            2 -> {
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

            // ---------- 通信ログ ----------
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

            // ---------- 緊急対応 ----------
            4 -> {
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

            // ---------- 関所 ----------
            5 -> {
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
                    showTab(2)
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
                    showTab(2)
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
                            showTab(3)
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
                    showTab(3)
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
        contentArea.postDelayed({ if (currentTab == 2) showTab(2) }, 300)
    }

    private var currentTab = 0

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST && resultCode == Activity.RESULT_OK) {
            val i = Intent(this, DnsMonitorService::class.java).apply {
                action = DnsMonitorService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            contentArea.postDelayed({ if (currentTab == 2) showTab(2) }, 500)
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
