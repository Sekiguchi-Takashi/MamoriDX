package com.appathy.mamoridx

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Phase 7: 接続中のWi-Fi／ルーターの安全性評価。
 * ポート確認は「自分が接続している回線のゲートウェイ」に対してのみ行う。
 */
object RouterCheck {

    const val SEV_OK = 0
    const val SEV_WARN = 1
    const val SEV_CRIT = 2

    data class Finding(val title: String, val detail: String, val severity: Int)

    data class Result(
        val ssid: String,
        val security: String,
        val securitySeverity: Int,
        val gateway: String,
        val dns: String,
        val openPorts: List<Pair<Int, String>>,
        val findings: List<Finding>,
        val grade: String,
        val error: String? = null
    )

    /** 確認するポートと、それが開いている場合の意味 */
    private val portTable = listOf(
        21 to "FTP（暗号化されないファイル転送）",
        22 to "SSH（遠隔操作）",
        23 to "Telnet（暗号化されない遠隔操作）",
        53 to "DNS",
        80 to "管理画面（暗号化なし）",
        139 to "NetBIOS（ファイル共有）",
        443 to "管理画面（暗号化あり）",
        445 to "SMB（ファイル共有）",
        1723 to "PPTP（旧式VPN）",
        5555 to "ADB（Android遠隔デバッグ）",
        7547 to "TR-069（プロバイダ遠隔管理）",
        8080 to "代替HTTP管理画面",
        8443 to "代替HTTPS管理画面",
        9100 to "プリンタ直接印刷"
    )

    /** 外部から接続されると危険度が高いポート */
    private val criticalPorts = setOf(21, 23, 445, 5555, 139, 1723)

    private val publicResolvers = setOf(
        "8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "9.9.9.9",
        "208.67.222.222", "208.67.220.220", "129.250.35.250", "129.250.35.251"
    )

    fun run(ctx: Context): Result {
        val findings = mutableListOf<Finding>()
        val wm = try {
            ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        } catch (e: Exception) {
            return Result("", "", SEV_WARN, "", "", emptyList(), emptyList(), "-",
                "Wi-Fi情報を取得できませんでした")
        }

        if (!wm.isWifiEnabled) {
            return Result("", "", SEV_WARN, "", "", emptyList(), emptyList(), "-",
                "Wi-FiがOFFです。診断したいWi-Fiに接続してから実行してください。")
        }

        @Suppress("DEPRECATION")
        val info = wm.connectionInfo
        val rawSsid = info?.ssid ?: ""
        val ssid = rawSsid.trim('"')
        if (ssid.isEmpty() || ssid == "<unknown ssid>") {
            return Result("", "", SEV_WARN, "", "", emptyList(), emptyList(), "-",
                "接続中のWi-Fi名を取得できませんでした。位置情報の権限と、" +
                "端末の位置情報設定がONになっているか確認してください。")
        }

        // ---- 暗号方式 ----
        var security = "判定できません"
        var secSev = SEV_WARN
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                when (info.currentSecurityType) {
                    0 -> { security = "暗号化なし（オープン）"; secSev = SEV_CRIT }
                    1 -> { security = "WEP（危険な旧方式）"; secSev = SEV_CRIT }
                    2 -> { security = "WPA/WPA2 パーソナル"; secSev = SEV_OK }
                    3 -> { security = "WPA/WPA2 エンタープライズ"; secSev = SEV_OK }
                    4 -> { security = "WPA3 パーソナル（SAE）"; secSev = SEV_OK }
                    5 -> { security = "WPA3 エンタープライズ(192bit)"; secSev = SEV_OK }
                    6 -> { security = "OWE（オープンだが暗号化あり）"; secSev = SEV_WARN }
                    else -> { security = "その他の方式"; secSev = SEV_WARN }
                }
            } catch (e: Exception) { }
        } else {
            try {
                @Suppress("DEPRECATION")
                val results = wm.scanResults
                val hit = results.firstOrNull { it.BSSID == info.bssid }
                val cap = hit?.capabilities ?: ""
                when {
                    cap.contains("SAE") -> { security = "WPA3（SAE）"; secSev = SEV_OK }
                    cap.contains("WPA2") -> { security = "WPA2"; secSev = SEV_OK }
                    cap.contains("WPA") -> { security = "WPA（旧方式）"; secSev = SEV_WARN }
                    cap.contains("WEP") -> { security = "WEP（危険な旧方式）"; secSev = SEV_CRIT }
                    cap.isNotEmpty() -> { security = "暗号化なし（オープン）"; secSev = SEV_CRIT }
                }
                if (cap.contains("WPS")) {
                    findings.add(Finding("WPSが有効です",
                        "ボタン一つで接続できる機能ですが、PINを総当たりされる既知の弱点があります。" +
                        "ルーターの設定画面でWPSをOFFにすることを推奨します。", SEV_WARN))
                }
            } catch (e: Exception) { }
        }

        when (secSev) {
            SEV_CRIT -> findings.add(Finding("暗号方式が危険です（$security）",
                "通信内容を近くの第三者に盗聴される恐れがあります。" +
                "ルーターの設定でWPA2またはWPA3に変更してください。", SEV_CRIT))
            SEV_WARN -> findings.add(Finding("暗号方式を確認してください（$security）",
                "可能であればWPA2以上、できればWPA3を使用してください。", SEV_WARN))
            else -> findings.add(Finding("暗号方式は良好です（$security）",
                "現行の推奨方式です。", SEV_OK))
        }

        // ---- ゲートウェイとDNS ----
        @Suppress("DEPRECATION")
        val dhcp = wm.dhcpInfo
        val gateway = ipToString(dhcp?.gateway ?: 0)
        val dns1 = ipToString(dhcp?.dns1 ?: 0)
        val dns2 = ipToString(dhcp?.dns2 ?: 0)
        val dnsText = listOf(dns1, dns2).filter { it.isNotEmpty() }.joinToString(", ")

        if (dns1.isNotEmpty() && dns1 != gateway && !publicResolvers.contains(dns1) &&
            !isPrivateIp(dns1)) {
            findings.add(Finding("DNSの向き先が外部の不明なサーバーです（$dns1）",
                "DNS設定が書き換えられていると、正規サイトのつもりで偽サイトへ誘導されます。" +
                "ルーターの設定を確認してください。", SEV_CRIT))
        }

        // ---- ゲートウェイのポート確認 ----
        val openPorts = mutableListOf<Pair<Int, String>>()
        if (gateway.isNotEmpty()) {
            val threads = mutableListOf<Thread>()
            val lock = Any()
            for ((port, label) in portTable) {
                val t = Thread {
                    if (isOpen(gateway, port, 500)) {
                        synchronized(lock) { openPorts.add(port to label) }
                    }
                }
                threads.add(t); t.start()
            }
            threads.forEach { try { it.join(2500) } catch (e: Exception) { } }
            openPorts.sortBy { it.first }

            for ((port, label) in openPorts) {
                if (criticalPorts.contains(port)) {
                    findings.add(Finding("危険なポートが開いています（$port: $label）",
                        "この機能は現在ほとんど不要で、乗っ取りの入口になります。" +
                        "ルーターの設定で無効化してください。", SEV_CRIT))
                }
            }
            if (openPorts.any { it.first == 80 } && openPorts.none { it.first == 443 }) {
                findings.add(Finding("管理画面が暗号化されていません（HTTP）",
                    "ルーターの管理パスワードが平文で流れます。" +
                    "HTTPS対応の機種なら有効化を、無理なら管理画面へのアクセスは有線のみに限定してください。",
                    SEV_WARN))
            }
            if (openPorts.any { it.first == 7547 }) {
                findings.add(Finding("プロバイダ遠隔管理ポート（TR-069）が開いています",
                    "プロバイダ提供機器では正常な場合がありますが、" +
                    "過去に大規模な乗っ取り被害の入口になった実績があります。" +
                    "レンタル機器でなければ無効化を検討してください。", SEV_WARN))
            }
        } else {
            findings.add(Finding("ゲートウェイのアドレスを取得できませんでした",
                "ポートの確認をスキップしました。", SEV_WARN))
        }

        val crit = findings.count { it.severity == SEV_CRIT }
        val warn = findings.count { it.severity == SEV_WARN }
        val grade = when {
            crit > 0 -> "C（要対策）"
            warn > 0 -> "B（要改善）"
            else -> "A（良好）"
        }

        return Result(ssid, security, secSev, gateway, dnsText, openPorts,
            findings.sortedByDescending { it.severity }, grade)
    }

    private fun isOpen(host: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), timeoutMs)
            true
        }
    } catch (e: Exception) { false }

    private fun ipToString(ip: Int): String {
        if (ip == 0) return ""
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }

    private fun isPrivateIp(ip: String): Boolean {
        val p = ip.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return false
        return p[0] == 10 ||
            (p[0] == 172 && p[1] in 16..31) ||
            (p[0] == 192 && p[1] == 168)
    }
}
