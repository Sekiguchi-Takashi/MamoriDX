package com.appathy.mamoridx

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 5〜8 のツール群。
 * 0: 外部メディア検査 / 1: SaaS接続確認 / 2: ルーター診断 / 3: 機器台帳
 */
class ToolsActivity : Activity() {

    companion object {
        const val EXTRA_PAGE = "page"
        private const val REQ_TREE = 2001
        private const val REQ_LOCATION = 2002
    }

    private val bgColor = Color.parseColor("#121212")
    private val cardColor = Color.parseColor("#1E1E1E")
    private val textColor = Color.parseColor("#EEEEEE")
    private val subColor = Color.parseColor("#9E9E9E")
    private val greenColor = Color.parseColor("#4CAF50")
    private val yellowColor = Color.parseColor("#FFC107")
    private val redColor = Color.parseColor("#F44336")
    private val accentColor = Color.parseColor("#03A9F4")

    private var page = 0
    private lateinit var contentArea: FrameLayout
    private lateinit var pageButtons: List<Button>

    // ---- 状態保持 ----
    private var mediaReport: MediaScanner.Report? = null
    private var mediaBusy = false
    private var mediaFolder = ""

    private var saasName = ""
    private var saasUrl = ""
    private var saasBusy = false
    private var saasDetail: String? = null

    private var routerResult: RouterCheck.Result? = null
    private var routerBusy = false

    private var assetName = ""
    private var assetKind = "ルーター"
    private var assetVer = ""
    private var assetUrl = ""
    private var assetPattern = ""
    private var assetBusy = false
    private var assetMessage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = intent.getIntExtra(EXTRA_PAGE, 0)
        SaasGuard.load(applicationContext)
        AssetLedger.load(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }

        root.addView(TextView(this).apply {
            text = "守りのDX ツール"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(18), dp(16), dp(10))
        })

        val selector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(8))
        }
        val names = listOf("メディア", "SaaS", "ルーター", "台帳")
        pageButtons = names.mapIndexed { i, n ->
            Button(this).apply {
                text = n
                textSize = 11f
                isAllCaps = false
                setPadding(0, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                setOnClickListener { showPage(i) }
            }
        }
        pageButtons.forEach { selector.addView(it) }
        root.addView(selector)

        contentArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(contentArea)

        setContentView(root)
        showPage(page)
    }

    private fun showPage(i: Int) {
        page = i
        pageButtons.forEachIndexed { idx, b ->
            b.setBackgroundColor(if (idx == i) accentColor else cardColor)
            b.setTextColor(if (idx == i) Color.BLACK else textColor)
        }
        contentArea.removeAllViews()
        contentArea.addView(
            when (i) {
                0 -> buildMediaPage()
                1 -> buildSaasPage()
                2 -> buildRouterPage()
                else -> buildAssetPage()
            }
        )
    }

    // =========================================================
    // Page 0: 外部メディア検査
    // =========================================================
    private fun buildMediaPage(): View {
        val list = column()

        list.addView(card().apply {
            addView(head("外部メディア検査"))
            addView(body(
                "USBメモリやSDカードの中身を調べ、危険な形式や偽装ファイルを指摘します。\n\n" +
                "確認する内容:\n" +
                "・実行形式やスクリプト（.exe .bat .js .apk など）\n" +
                "・二重拡張子（請求書.pdf.exe）\n" +
                "・文字の並びを逆転させる特殊文字によるファイル名偽装\n" +
                "・autorun.inf（差しただけで実行させる設定）\n" +
                "・マクロ付きOffice（.docm .xlsm）\n" +
                "・拡張子と中身の不一致（マジックバイト照合）"))
            addView(TextView(this@ToolsActivity).apply {
                text = "※ウイルス定義は持たないため、既知ウイルスの判定はできません。" +
                    "あくまで「開く前に気付く」ための道具です。"
                textSize = 12f
                setTextColor(yellowColor)
                setPadding(0, dp(8), 0, 0)
            })
        })

        list.addView(actionBtn(
            if (mediaBusy) "検査中…" else "フォルダを選んで検査", accentColor) {
            if (!mediaBusy) pickFolder()
        })

        if (mediaFolder.isNotEmpty()) {
            list.addView(card().apply {
                addView(body("検査対象: $mediaFolder"))
            })
        }

        val rep = mediaReport
        if (mediaBusy) {
            list.addView(card().apply { addView(body("検査しています。しばらくお待ちください…")) })
        } else if (rep != null) {
            val danger = rep.items.count { it.severity == MediaScanner.SEV_DANGER }
            val warn = rep.items.count { it.severity == MediaScanner.SEV_WARN }
            list.addView(card().apply {
                addView(TextView(this@ToolsActivity).apply {
                    text = when {
                        rep.items.isEmpty() -> "✓ 気になるファイルはありませんでした"
                        danger > 0 -> "⚠ 危険 $danger 件 / 注意 $warn 件"
                        else -> "注意 $warn 件"
                    }
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(when {
                        rep.items.isEmpty() -> greenColor
                        danger > 0 -> redColor
                        else -> yellowColor
                    })
                })
                addView(body("走査したファイル ${rep.totalFiles} 件 / フォルダ ${rep.totalDirs} 件" +
                    if (rep.truncated) "\n（件数が多いため途中で打ち切りました）" else ""))
            })

            rep.items.forEach { item ->
                val c = if (item.severity == MediaScanner.SEV_DANGER) redColor else yellowColor
                list.addView(card().apply {
                    addView(TextView(this@ToolsActivity).apply {
                        text = (if (item.severity == MediaScanner.SEV_DANGER) "【危険】" else "【注意】") +
                            item.name
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(c)
                    })
                    addView(TextView(this@ToolsActivity).apply {
                        text = item.path
                        textSize = 11f
                        setTextColor(subColor)
                        setPadding(0, dp(2), 0, dp(4))
                    })
                    addView(body(item.reasons.joinToString("\n") { "・$it" }))
                })
            }
        }

        return ScrollView(this).apply { addView(list) }
    }

    private fun pickFolder() {
        try {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivityForResult(i, REQ_TREE)
        } catch (e: Exception) {
            toast("フォルダ選択画面を開けませんでした")
        }
    }

    // =========================================================
    // Page 1: SaaS接続確認
    // =========================================================
    private fun buildSaasPage(): View {
        val list = column()

        list.addView(card().apply {
            addView(head("SaaS接続確認（公衆Wi-Fi対策）"))
            addView(body(
                "契約中のSaaSのログインURLを登録し、安全な回線で「基準」を記録します。\n" +
                "外出先で確認すると、別サイトへのリダイレクトや証明書のすり替え" +
                "（中間者攻撃）を検知できます。\n\n" +
                "使い方:\n" +
                "手順1. 自宅など信頼できる回線で、名前とURLを登録\n" +
                "手順2. 「安全な回線で基準登録」を押す\n" +
                "手順3. 外出先で「今すぐ確認」を押して照合する"))
        })

        // 追加フォーム
        list.addView(card().apply {
            addView(head("登録"))
            val nameEt = editText("サービス名（例: 社内Kintone）", saasName)
            val urlEt = editText("https://example.cybozu.com/", saasUrl)
            addView(nameEt); addView(urlEt)
            addView(actionBtn("この設定を追加", greenColor) {
                saasName = nameEt.text.toString().trim()
                saasUrl = urlEt.text.toString().trim()
                if (saasName.isEmpty() || saasUrl.isEmpty()) {
                    toast("サービス名とURLを入力してください")
                } else {
                    SaasGuard.add(applicationContext, saasName, saasUrl)
                    saasName = ""; saasUrl = ""
                    showPage(1)
                }
            })
        })

        if (saasBusy) {
            list.addView(card().apply { addView(body("接続を確認しています…")) })
        }

        SaasGuard.load(applicationContext).forEach { site ->
            val lv = site.lastLevel
            val c = when (lv) {
                SaasGuard.LEVEL_DANGER -> redColor
                SaasGuard.LEVEL_WARN -> yellowColor
                SaasGuard.LEVEL_OK -> greenColor
                else -> subColor
            }
            list.addView(card().apply {
                addView(TextView(this@ToolsActivity).apply {
                    text = site.name
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(textColor)
                })
                addView(TextView(this@ToolsActivity).apply {
                    text = site.url
                    textSize = 11f
                    setTextColor(subColor)
                    setPadding(0, dp(2), 0, dp(4))
                })
                addView(TextView(this@ToolsActivity).apply {
                    text = if (site.baseHost.isEmpty()) "基準: 未登録"
                    else "基準: ${site.baseHost}\n証明書: ${SaasGuard.shortFp(site.baseFingerprint)}"
                    textSize = 12f
                    setTextColor(textColor)
                })
                if (site.lastCheck > 0) {
                    addView(TextView(this@ToolsActivity).apply {
                        text = "前回: ${site.lastResult}（${fmt(site.lastCheck)}）"
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(c)
                        setPadding(0, dp(4), 0, 0)
                    })
                }
                addView(LinearLayout(this@ToolsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                    addView(smallBtn("基準登録", accentColor) {
                        runSaas(site.id, true)
                    })
                    addView(smallBtn("今すぐ確認", greenColor) {
                        runSaas(site.id, false)
                    })
                    addView(smallBtn("削除", cardColor, textColor) {
                        confirmDelete("「${site.name}」を削除しますか？") {
                            SaasGuard.remove(applicationContext, site.id)
                            showPage(1)
                        }
                    })
                })
            })
        }

        val d = saasDetail
        if (d != null) {
            list.addView(card().apply {
                addView(head("最新の確認結果"))
                addView(body(d))
            })
        }

        return ScrollView(this).apply { addView(list) }
    }

    private fun runSaas(id: String, baseline: Boolean) {
        if (saasBusy) return
        val site = SaasGuard.find(id) ?: return
        saasBusy = true
        showPage(1)
        Thread {
            val p = SaasGuard.probe(site.url)
            val text: String
            if (baseline) {
                val ok = SaasGuard.registerBaseline(applicationContext, site, p)
                text = if (ok)
                    "基準を登録しました。\n\n最終接続先: ${p.finalHost}\n" +
                    "証明書: ${SaasGuard.shortFp(p.fingerprint)}\n\n" +
                    "経路:\n　${p.chain.joinToString("\n　→ ")}"
                else "基準登録に失敗しました: ${p.error ?: "不明なエラー"}"
            } else {
                val v = SaasGuard.verify(site, p)
                SaasGuard.recordResult(applicationContext, site, v)
                text = "判定: ${v.summary}\n\n" + v.details.joinToString("\n\n")
            }
            runOnUiThread {
                saasBusy = false
                saasDetail = "【${site.name}】\n$text"
                showPage(1)
            }
        }.start()
    }

    // =========================================================
    // Page 2: ルーター診断
    // =========================================================
    private fun buildRouterPage(): View {
        val list = column()

        list.addView(card().apply {
            addView(head("Wi-Fi／ルーター診断"))
            addView(body(
                "接続中のWi-Fiの暗号方式と、ルーター（ゲートウェイ）の開放ポート、" +
                "DNSの向き先を確認して総合評価します。\n\n" +
                "手順1. 診断したいWi-Fi（自宅など）に接続する\n" +
                "手順2. 「診断を実行」を押す\n" +
                "手順3. 位置情報の許可を求められたら許可する\n" +
                "　（AndroidではWi-Fi名の取得に位置情報権限が必要な仕様のためです）\n" +
                "手順4. 結果の×項目をルーターの設定画面で修正する"))
            addView(TextView(this@ToolsActivity).apply {
                text = "※必ず自分が管理する回線でのみ実行してください。" +
                    "他人のネットワークへのポート確認は法的問題になり得ます。"
                textSize = 12f
                setTextColor(yellowColor)
                setPadding(0, dp(8), 0, 0)
            })
        })

        list.addView(actionBtn(if (routerBusy) "診断中…" else "診断を実行", accentColor) {
            if (!routerBusy) startRouterCheck()
        })

        val r = routerResult
        if (routerBusy) {
            list.addView(card().apply {
                addView(body("ルーターを確認しています（最大10秒程度）…"))
            })
        } else if (r != null) {
            if (r.error != null) {
                list.addView(card().apply {
                    addView(TextView(this@ToolsActivity).apply {
                        text = r.error
                        textSize = 13f
                        setTextColor(yellowColor)
                    })
                })
            } else {
                val gc = when {
                    r.grade.startsWith("A") -> greenColor
                    r.grade.startsWith("B") -> yellowColor
                    else -> redColor
                }
                list.addView(card().apply {
                    addView(TextView(this@ToolsActivity).apply {
                        text = "総合評価: ${r.grade}"
                        textSize = 18f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(gc)
                        gravity = Gravity.CENTER
                    })
                    addView(body(
                        "接続先: ${r.ssid}\n" +
                        "暗号方式: ${r.security}\n" +
                        "ルーター: ${r.gateway.ifEmpty { "取得できず" }}\n" +
                        "DNS: ${r.dns.ifEmpty { "取得できず" }}"))
                })

                if (r.openPorts.isNotEmpty()) {
                    list.addView(card().apply {
                        addView(head("開いているポート"))
                        addView(body(r.openPorts.joinToString("\n") {
                            "・${it.first} … ${it.second}"
                        }))
                    })
                }

                r.findings.forEach { f ->
                    val c = when (f.severity) {
                        RouterCheck.SEV_CRIT -> redColor
                        RouterCheck.SEV_WARN -> yellowColor
                        else -> greenColor
                    }
                    list.addView(card().apply {
                        addView(TextView(this@ToolsActivity).apply {
                            text = (if (f.severity == RouterCheck.SEV_OK) "○ " else "× ") + f.title
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(c)
                        })
                        addView(body(f.detail))
                    })
                }
            }
        }

        return ScrollView(this).apply { addView(list) }
    }

    private fun startRouterCheck() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_LOCATION)
            return
        }
        routerBusy = true
        showPage(2)
        Thread {
            val res = try {
                RouterCheck.run(applicationContext)
            } catch (e: Exception) {
                RouterCheck.Result("", "", 1, "", "", emptyList(), emptyList(), "-",
                    "診断中にエラーが発生しました: ${e.javaClass.simpleName}")
            }
            runOnUiThread {
                routerBusy = false
                routerResult = res
                showPage(2)
            }
        }.start()
    }

    // =========================================================
    // Page 3: 機器台帳
    // =========================================================
    private fun buildAssetPage(): View {
        val list = column()

        list.addView(card().apply {
            addView(head("機器バージョン台帳"))
            addView(body(
                "ルーターやPCの現在のバージョンを記録し、メーカーの更新情報ページを" +
                "定期的に取得して変化を検知します。\n\n" +
                "手順1. 機器名・種別・現在のバージョンを入力\n" +
                "手順2. メーカーの更新情報ページのURLを入力\n" +
                "手順3. 「更新確認」を押すと、ページ内の最新版表記と前回結果を比較\n" +
                "手順4. 変化があると赤く表示される\n" +
                "手順5. 実際に更新したら「台帳を最新に更新」で記録を合わせる"))
            addView(TextView(this@ToolsActivity).apply {
                text = "※メーカーページの書式は千差万別のため、自動抽出できない場合があります。" +
                    "その場合もページ内容の変化は検知できます。"
                textSize = 12f
                setTextColor(yellowColor)
                setPadding(0, dp(8), 0, 0)
            })
        })

        list.addView(card().apply {
            addView(head("機器を追加"))
            val nEt = editText("機器名（例: 自宅ルーター WSR-3200）", assetName)
            val kEt = editText("種別（ルーター/PC/NAS など）", assetKind)
            val vEt = editText("現在のバージョン（例: 1.12）", assetVer)
            val uEt = editText("更新情報ページのURL", assetUrl)
            val pEt = editText("抽出パターン（省略可）", assetPattern)
            addView(nEt); addView(kEt); addView(vEt); addView(uEt); addView(pEt)
            addView(actionBtn("台帳に追加", greenColor) {
                assetName = nEt.text.toString().trim()
                assetKind = kEt.text.toString().trim()
                assetVer = vEt.text.toString().trim()
                assetUrl = uEt.text.toString().trim()
                assetPattern = pEt.text.toString().trim()
                if (assetName.isEmpty() || assetUrl.isEmpty()) {
                    toast("機器名とURLは必須です")
                } else {
                    AssetLedger.add(applicationContext, assetName, assetKind,
                        assetVer, assetUrl, assetPattern)
                    assetName = ""; assetVer = ""; assetUrl = ""; assetPattern = ""
                    showPage(3)
                }
            })
        })

        if (assetBusy) {
            list.addView(card().apply { addView(body("更新情報を取得しています…")) })
        }
        val am = assetMessage
        if (am != null) {
            list.addView(card().apply {
                addView(head("確認結果"))
                addView(body(am))
            })
        }

        AssetLedger.load(applicationContext).forEach { a ->
            list.addView(card().apply {
                if (a.changed) setBackgroundColor(Color.parseColor("#3A1E1E"))
                addView(TextView(this@ToolsActivity).apply {
                    text = a.name
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (a.changed) redColor else textColor)
                })
                addView(TextView(this@ToolsActivity).apply {
                    text = "${a.kind}　台帳のバージョン: ${a.currentVersion.ifEmpty { "未記入" }}"
                    textSize = 12f
                    setTextColor(subColor)
                    setPadding(0, dp(2), 0, dp(2))
                })
                if (a.lastCheck > 0) {
                    addView(TextView(this@ToolsActivity).apply {
                        text = (if (a.changed) "⚠ 変化あり" else "変化なし") +
                            "　最終確認: ${fmt(a.lastCheck)}" +
                            (if (a.lastFound.isNotEmpty()) "\nページ上の最新: ${a.lastFound}" else "") +
                            (if (a.changed && a.prevFound.isNotEmpty()) "（前回: ${a.prevFound}）" else "")
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(if (a.changed) redColor else greenColor)
                    })
                }
                addView(TextView(this@ToolsActivity).apply {
                    text = a.checkUrl
                    textSize = 10f
                    setTextColor(subColor)
                    setPadding(0, dp(4), 0, 0)
                })
                addView(LinearLayout(this@ToolsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) }
                    addView(smallBtn("更新確認", accentColor) { runAssetCheck(a.id) })
                    addView(smallBtn("ページを開く", cardColor, textColor) {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.checkUrl)))
                        } catch (e: Exception) { toast("開けませんでした") }
                    })
                })
                addView(LinearLayout(this@ToolsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(6) }
                    addView(smallBtn("台帳を最新に更新", greenColor) {
                        AssetLedger.acknowledge(applicationContext, a.id)
                        showPage(3)
                    })
                    addView(smallBtn("削除", cardColor, textColor) {
                        confirmDelete("「${a.name}」を台帳から削除しますか？") {
                            AssetLedger.remove(applicationContext, a.id)
                            showPage(3)
                        }
                    })
                })
            })
        }

        return ScrollView(this).apply { addView(list) }
    }

    private fun runAssetCheck(id: String) {
        if (assetBusy) return
        val a = AssetLedger.find(id) ?: return
        assetBusy = true
        showPage(3)
        Thread {
            val o = AssetLedger.check(applicationContext, a)
            runOnUiThread {
                assetBusy = false
                assetMessage = "【${a.name}】\n${o.message}"
                showPage(3)
            }
        }.start()
    }

    // =========================================================
    // 結果受け取り
    // =========================================================
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_TREE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { }
            mediaFolder = uri.lastPathSegment ?: uri.toString()
            mediaBusy = true
            mediaReport = null
            showPage(0)
            Thread {
                val rep = try {
                    MediaScanner.scan(applicationContext, uri)
                } catch (e: Exception) {
                    MediaScanner.Report(0, 0, false, emptyList())
                }
                runOnUiThread {
                    mediaBusy = false
                    mediaReport = rep
                    showPage(0)
                }
            }.start()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRouterCheck()
            } else {
                toast("位置情報の権限がないとWi-Fi名を取得できません")
            }
        }
    }

    // =========================================================
    // UI部品
    // =========================================================
    private fun column(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), 0, dp(12), dp(24))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(cardColor)
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
        setTextColor(accentColor)
    }

    private fun body(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(textColor)
        setLineSpacing(dp(2).toFloat(), 1f)
        setPadding(0, dp(6), 0, 0)
    }

    private fun editText(hintText: String, value: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 13f
        setTextColor(textColor)
        setHintTextColor(subColor)
        setBackgroundColor(bgColor)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setText(value)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) }
    }

    private fun actionBtn(label: String, bg: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.BLACK)
            setBackgroundColor(bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
            ).apply { setMargins(0, dp(10), 0, 0) }
            setOnClickListener { onClick() }
        }

    private fun smallBtn(label: String, bg: Int, fg: Int = Color.BLACK,
                         onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 11f
            isAllCaps = false
            setPadding(0, 0, 0, 0)
            setTextColor(fg)
            setBackgroundColor(bg)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            setOnClickListener { onClick() }
        }

    private fun confirmDelete(msg: String, onYes: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("確認")
            .setMessage(msg)
            .setPositiveButton("削除") { _, _ -> onYes() }
            .setNegativeButton("やめる", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun fmt(t: Long): String =
        SimpleDateFormat("M/d HH:mm", Locale.JAPAN).format(Date(t))

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
