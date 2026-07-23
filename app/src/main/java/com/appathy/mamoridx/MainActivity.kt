package com.appathy.mamoridx

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        val tabNames = listOf("アプリ棚卸し", "端末診断", "説明書")
        tabButtons = tabNames.mapIndexed { index, name ->
            Button(this).apply {
                text = name
                textSize = 13f
                isAllCaps = false
                setTextColor(textColor)
                setBackgroundColor(cardColor)
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(dp(4), 0, dp(4), 0)
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
        tabButtons.forEachIndexed { i, b ->
            b.setBackgroundColor(if (i == index) accentColor else cardColor)
            b.setTextColor(if (i == index) Color.BLACK else textColor)
        }
        contentArea.removeAllViews()
        when (index) {
            0 -> contentArea.addView(buildInventoryView())
            1 -> contentArea.addView(buildPostureView())
            2 -> contentArea.addView(buildManualView())
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
    // タブ3: 説明書
    // =========================================================
    private fun buildManualView(): View {
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

        section("守りのDX 2.0 とは",
            "従来のセキュリティ対策は「会社が許可していないアプリ（シャドーIT）を見つけたら即削除」でした。" +
            "しかし現場が便利なSaaSを使うのには理由があります。\n\n" +
            "守りのDX 2.0は発想を変えます。\n" +
            "①まず可視化する\n②有益なものは使い続けられるようにする\n③その代わり漏洩しない仕組み（ガードレール）を置く\n\n" +
            "禁止ではなく、安全に使うための土台づくりです。")

        section("アプリ棚卸しタブの見方",
            "端末にインストールされたアプリを自動で3分類します。\n\n" +
            "● 許可済み（緑）: ストア入手・権限も最小限。そのまま利用OK\n" +
            "● 要監視（黄）: 危険権限が多め。権限の見直しで緑にできます\n" +
            "● 要対策（赤）: ストア外入手、または権限が非常に多い。入手元の確認を\n\n" +
            "リスクスコアは「付与済みの危険権限1つ＝1点、ストア外入手＝+3点」で計算しています。" +
            "アプリをタップすると詳細と対処方針が表示されます。")

        section("端末診断タブの見方",
            "端末そのものの防御力を6項目でチェックし、A/B/Cで判定します。\n\n" +
            "×が付いた項目には対処方法を表示します。特に「画面ロック未設定」と" +
            "「セキュリティパッチが半年以上前」は優先して対応してください。")

        section("このアプリが集めない情報",
            "このアプリは通信権限を持たず、外部に一切データを送信しません。" +
            "診断結果はすべて端末内で計算され、保存もされません。" +
            "アプリを閉じれば結果は消えます。")

        section("今後のロードマップ",
            "Phase 2: 通信の可視化（どのアプリがどのSaaSと通信しているかをDNSレベルで記録）\n" +
            "Phase 3: 漏洩ガード（共有時にマイナンバー・クレカ番号・社外秘キーワードを検査する関所機能）")

        section("Appathy",
            "Less Motivation, More Automation.\n本アプリはスマホのみ（Termux + GitHub Actions）で開発されています。")

        return ScrollView(this).apply { addView(list) }
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
