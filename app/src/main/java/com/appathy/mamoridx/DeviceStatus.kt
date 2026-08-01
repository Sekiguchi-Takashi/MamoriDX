package com.appathy.mamoridx

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 端末の現在の状態をまとめて取得する。
 * Android 11以降の制約により一部は取得範囲が限られる（各所にnoteとして明記）。
 */
object DeviceStatus {

    data class Storage(
        val totalBytes: Long,
        val usedBytes: Long,
        val freeBytes: Long,
        val percent: Int,
        val volumes: List<VolumeInfo>
    )

    data class VolumeInfo(
        val name: String,
        val totalBytes: Long,
        val usedBytes: Long,
        val removable: Boolean
    )

    data class UsbDevice(
        val name: String,
        val detail: String,
        val kind: String
    )

    data class VersionInfo(
        val release: String,
        val sdkInt: Int,
        val codeName: String,
        val securityPatch: String,
        val patchAgeDays: Long,
        val model: String,
        val manufacturer: String,
        val buildId: String,
        val updateHint: String,
        val needsAttention: Boolean
    )

    data class RunningApp(
        val label: String,
        val packageName: String,
        val importance: String,
        val isForeground: Boolean
    )

    data class WifiInfo(
        val connected: Boolean,
        val ssid: String,
        val security: String,
        val linkSpeed: String,
        val ip: String,
        val note: String
    )

    data class BtDevice(val name: String, val type: String)

    data class BtInfo(
        val supported: Boolean,
        val enabled: Boolean,
        val connected: List<BtDevice>,
        val bonded: List<BtDevice>,
        val note: String
    )

    // =========================================================
    // ストレージ
    // =========================================================
    fun storage(ctx: Context): Storage {
        val volumes = mutableListOf<VolumeInfo>()
        var total = 0L
        var free = 0L

        // 内部ストレージ（ユーザー領域）
        try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.absolutePath)
            val t = stat.blockCountLong * stat.blockSizeLong
            val f = stat.availableBlocksLong * stat.blockSizeLong
            total += t; free += f
            volumes.add(VolumeInfo("内部ストレージ", t, t - f, false))
        } catch (e: Exception) { }

        // 外部（SDカード等）: getExternalFilesDirsの2番目以降が着脱可能領域
        try {
            val dirs = ctx.getExternalFilesDirs(null)
            for (i in dirs.indices) {
                val d = dirs[i] ?: continue
                if (i == 0) continue // 0番目は内部ストレージのアプリ領域
                if (!isMounted(d)) continue
                val stat = StatFs(d.absolutePath)
                val t = stat.blockCountLong * stat.blockSizeLong
                val f = stat.availableBlocksLong * stat.blockSizeLong
                total += t; free += f
                volumes.add(VolumeInfo("外部ストレージ ${i}", t, t - f, true))
            }
        } catch (e: Exception) { }

        val used = total - free
        val pct = if (total > 0) ((used * 100) / total).toInt() else 0
        return Storage(total, used, free, pct, volumes)
    }

    private fun isMounted(dir: File): Boolean = try {
        Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED
    } catch (e: Exception) { false }

    fun formatBytes(b: Long): String {
        if (b <= 0) return "0 B"
        val gb = b.toDouble() / (1000.0 * 1000.0 * 1000.0)
        if (gb >= 1.0) return String.format(Locale.US, "%.1f GB", gb)
        val mb = b.toDouble() / (1000.0 * 1000.0)
        if (mb >= 1.0) return String.format(Locale.US, "%.0f MB", mb)
        return String.format(Locale.US, "%.0f KB", b.toDouble() / 1000.0)
    }

    // =========================================================
    // 外部デバイス（USB）
    // =========================================================
    fun usbDevices(ctx: Context): List<UsbDevice> {
        val out = mutableListOf<UsbDevice>()

        // USBホスト接続の機器
        try {
            val um = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
            for ((_, dev) in um.deviceList) {
                val name = if (Build.VERSION.SDK_INT >= 21)
                    (dev.productName ?: "USB機器") else "USB機器"
                val maker = if (Build.VERSION.SDK_INT >= 21)
                    (dev.manufacturerName ?: "") else ""
                val kind = usbClassName(dev.deviceClass, dev)
                val detail = buildString {
                    if (maker.isNotEmpty()) append("メーカー: $maker\n")
                    append("種別: $kind\n")
                    append(String.format("ベンダーID: 0x%04X / プロダクトID: 0x%04X",
                        dev.vendorId, dev.productId))
                }
                out.add(UsbDevice(name, detail, kind))
            }
        } catch (e: Exception) { }

        // マウント済みの外部ストレージ（USBメモリ/SDカード）
        try {
            val dirs = ctx.getExternalFilesDirs(null)
            for (i in dirs.indices) {
                if (i == 0) continue
                val d = dirs[i] ?: continue
                if (!isMounted(d)) continue
                val stat = StatFs(d.absolutePath)
                val t = stat.blockCountLong * stat.blockSizeLong
                val f = stat.availableBlocksLong * stat.blockSizeLong
                out.add(UsbDevice(
                    "外部ストレージ（USBメモリ/SDカード）",
                    "容量: ${formatBytes(t)}　空き: ${formatBytes(f)}\n" +
                    "検査はツール→外部メディア検査から行えます",
                    "ストレージ"))
            }
        } catch (e: Exception) { }

        return out
    }

    private fun usbClassName(cls: Int, dev: android.hardware.usb.UsbDevice): String {
        // デバイスクラスが0の場合はインターフェースを見る
        var c = cls
        if (c == 0 && dev.interfaceCount > 0) {
            try { c = dev.getInterface(0).interfaceClass } catch (e: Exception) { }
        }
        return when (c) {
            1 -> "オーディオ"
            2 -> "通信"
            3 -> "入力機器（キーボード/マウス）"
            5 -> "物理デバイス"
            6 -> "カメラ/画像"
            7 -> "プリンタ"
            8 -> "ストレージ"
            9 -> "USBハブ"
            10 -> "データ通信"
            11 -> "ICカード"
            14 -> "映像"
            224 -> "無線（Bluetooth等）"
            255 -> "メーカー独自"
            else -> "その他(クラス$c)"
        }
    }

    // =========================================================
    // Androidバージョンと更新
    // =========================================================
    fun version(): VersionInfo {
        val patch = Build.VERSION.SECURITY_PATCH ?: ""
        var ageDays = -1L
        try {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(patch)
            if (d != null) ageDays = (Date().time - d.time) / (1000L * 60 * 60 * 24)
        } catch (e: Exception) { }

        val sdk = Build.VERSION.SDK_INT
        val codeName = androidName(sdk)

        val hints = mutableListOf<String>()
        var attention = false

        if (sdk < 30) {
            hints.add("このOSは提供元のセキュリティ更新が終了している可能性が高いです。" +
                "機種変更または延命策の検討をおすすめします。")
            attention = true
        } else if (sdk < 33) {
            hints.add("より新しいAndroidが提供されている可能性があります。" +
                "端末の設定でシステム更新を確認してください。")
            attention = true
        }

        if (ageDays in 0..180) {
            hints.add("セキュリティパッチは${ageDays}日前で、良好な状態です。")
        } else if (ageDays > 180) {
            hints.add("セキュリティパッチが${ageDays}日前（半年以上）です。" +
                "更新が提供されていないか確認してください。")
            attention = true
        }

        hints.add("実際の更新有無は端末の「システム更新」画面でのみ確認できます。" +
            "下のボタンから開けます。")

        return VersionInfo(
            release = Build.VERSION.RELEASE ?: "不明",
            sdkInt = sdk,
            codeName = codeName,
            securityPatch = patch.ifEmpty { "不明" },
            patchAgeDays = ageDays,
            model = Build.MODEL ?: "不明",
            manufacturer = Build.MANUFACTURER ?: "不明",
            buildId = Build.DISPLAY ?: (Build.ID ?: "不明"),
            updateHint = hints.joinToString("\n\n"),
            needsAttention = attention
        )
    }

    private fun androidName(sdk: Int): String = when {
        sdk >= 35 -> "Android 15 以降"
        sdk == 34 -> "Android 14"
        sdk == 33 -> "Android 13"
        sdk == 32 || sdk == 31 -> "Android 12"
        sdk == 30 -> "Android 11"
        sdk == 29 -> "Android 10"
        sdk == 28 -> "Android 9"
        sdk == 27 || sdk == 26 -> "Android 8"
        else -> "Android (API $sdk)"
    }

    // =========================================================
    // 起動中のアプリ
    // =========================================================
    /**
     * Android 5.0以降、他アプリの実行状態はOSの仕様で取得が制限されている。
     * 取得できる範囲（自プロセス＋一部の実行中サービス保持アプリ）を返す。
     */
    fun runningApps(ctx: Context): Pair<List<RunningApp>, String> {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = ctx.packageManager
        val map = LinkedHashMap<String, RunningApp>()

        // 実行中プロセス（多くの端末では自アプリのみ返る）
        try {
            val procs = am.runningAppProcesses
            if (procs != null) {
                for (p in procs) {
                    val pkg = p.pkgList?.firstOrNull() ?: continue
                    val imp = importanceName(p.importance)
                    val fg = p.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
                    map[pkg] = RunningApp(labelOf(pm, pkg) ?: pkg, pkg, imp, fg)
                }
            }
        } catch (e: Exception) { }

        // 実行中サービスを持つアプリ（端末により取得範囲が異なる）
        try {
            @Suppress("DEPRECATION")
            val services = am.getRunningServices(200)
            if (services != null) {
                for (s in services) {
                    val pkg = s.service?.packageName ?: continue
                    if (map.containsKey(pkg)) continue
                    map[pkg] = RunningApp(
                        labelOf(pm, pkg) ?: pkg, pkg,
                        if (s.foreground) "常駐（通知あり）" else "バックグラウンド動作",
                        false)
                }
            }
        } catch (e: Exception) { }

        val note = if (map.size <= 2)
            "Android 5.0以降のOS仕様により、他のアプリの実行状態はほとんど取得できません。" +
            "全体を確認するには、端末の「最近使ったアプリ」画面や" +
            "設定→アプリ→実行中のサービスをご覧ください。"
        else
            "OSの制限により、ここに表示されるのは実行中のアプリの一部です。" +
            "常駐しているアプリの全体像は端末の設定画面で確認できます。"

        val list = map.values.sortedWith(
            compareByDescending<RunningApp> { it.isForeground }.thenBy { it.label })
        return list to note
    }

    private fun importanceName(v: Int): String = when {
        v <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "画面に表示中"
        v <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "表示中（前面ではない）"
        v <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "サービス動作中"
        else -> "バックグラウンド"
    }

    private fun labelOf(pm: PackageManager, pkg: String): String? = try {
        val ai: ApplicationInfo = pm.getApplicationInfo(pkg, 0)
        ai.loadLabel(pm).toString()
    } catch (e: Exception) { null }

    // =========================================================
    // Wi-Fi
    // =========================================================
    fun wifi(ctx: Context, hasLocationPermission: Boolean): WifiInfo {
        return try {
            val wm = ctx.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wm.isWifiEnabled) {
                return WifiInfo(false, "", "", "", "", "Wi-FiがOFFです")
            }
            @Suppress("DEPRECATION")
            val info = wm.connectionInfo
            @Suppress("DEPRECATION")
            val raw = info?.ssid ?: ""
            val ssid = raw.trim('"')
            if (ssid.isEmpty() || ssid == "<unknown ssid>") {
                val note = if (!hasLocationPermission)
                    "Wi-Fi名の表示には位置情報の許可が必要です（Androidの仕様）。" +
                    "下のボタンから許可できます。"
                else "Wi-Fiに接続していないか、名前を取得できませんでした。"
                return WifiInfo(false, "", "", "", "", note)
            }

            var security = ""
            if (Build.VERSION.SDK_INT >= 31) {
                security = when (info.currentSecurityType) {
                    0 -> "暗号化なし（オープン）"
                    1 -> "WEP（危険）"
                    2 -> "WPA/WPA2 パーソナル"
                    3 -> "WPA/WPA2 エンタープライズ"
                    4 -> "WPA3 パーソナル"
                    5 -> "WPA3 エンタープライズ"
                    6 -> "OWE"
                    else -> "不明"
                }
            }

            @Suppress("DEPRECATION")
            val speed = if (info.linkSpeed > 0) "${info.linkSpeed} Mbps" else ""
            @Suppress("DEPRECATION")
            val ipInt = info.ipAddress
            val ip = if (ipInt != 0)
                "${ipInt and 0xFF}.${(ipInt shr 8) and 0xFF}." +
                "${(ipInt shr 16) and 0xFF}.${(ipInt shr 24) and 0xFF}" else ""

            WifiInfo(true, ssid, security, speed, ip,
                "詳しい安全性の診断はツール→ルーター診断で行えます。")
        } catch (e: Exception) {
            WifiInfo(false, "", "", "", "", "Wi-Fi情報を取得できませんでした")
        }
    }

    // =========================================================
    // Bluetooth
    // =========================================================
    fun bluetooth(ctx: Context, hasBtPermission: Boolean): BtInfo {
        val adapter: BluetoothAdapter? = try {
            if (Build.VERSION.SDK_INT >= 18) {
                val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                bm?.adapter
            } else null
        } catch (e: Exception) { null }

        if (adapter == null) {
            return BtInfo(false, false, emptyList(), emptyList(),
                "この端末はBluetoothに対応していないか、情報を取得できません")
        }
        if (Build.VERSION.SDK_INT >= 31 && !hasBtPermission) {
            return BtInfo(true, false, emptyList(), emptyList(),
                "Bluetooth機器名の表示には「近くのデバイス」の許可が必要です。" +
                "下のボタンから許可できます。")
        }
        val enabled = try { adapter.isEnabled } catch (e: Exception) { false }
        if (!enabled) {
            return BtInfo(true, false, emptyList(), emptyList(), "BluetoothがOFFです")
        }

        val connected = mutableListOf<BtDevice>()
        val bonded = mutableListOf<BtDevice>()

        // 接続中（プロファイル単位で接続状態を確認）
        try {
            val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val profiles = listOf(
                BluetoothProfile.GATT to "低消費電力機器",
                BluetoothProfile.GATT_SERVER to "低消費電力機器"
            )
            for ((profile, _) in profiles) {
                try {
                    val devs = bm.getConnectedDevices(profile)
                    for (d in devs) {
                        val n = safeName(d)
                        if (connected.none { it.name == n }) {
                            connected.add(BtDevice(n, deviceTypeName(d)))
                        }
                    }
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }

        // ペアリング済み
        try {
            for (d in adapter.bondedDevices ?: emptySet()) {
                bonded.add(BtDevice(safeName(d), deviceTypeName(d)))
            }
        } catch (e: Exception) { }

        val note = if (connected.isEmpty())
            "接続中の機器は検出されませんでした。イヤホン等は" +
            "OSの仕様で接続中と判定されない場合があるため、" +
            "下のペアリング済み一覧と、端末のBluetooth設定もあわせてご確認ください。"
        else "接続中の機器を表示しています。"

        return BtInfo(true, true, connected, bonded, note)
    }

    private fun safeName(d: android.bluetooth.BluetoothDevice): String = try {
        d.name ?: d.address ?: "不明な機器"
    } catch (e: SecurityException) { "（名前の取得に権限が必要）" }
    catch (e: Exception) { "不明な機器" }

    private fun deviceTypeName(d: android.bluetooth.BluetoothDevice): String = try {
        when (d.bluetoothClass?.majorDeviceClass) {
            android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> "オーディオ/映像"
            android.bluetooth.BluetoothClass.Device.Major.COMPUTER -> "パソコン"
            android.bluetooth.BluetoothClass.Device.Major.PHONE -> "電話"
            android.bluetooth.BluetoothClass.Device.Major.PERIPHERAL -> "入力機器"
            android.bluetooth.BluetoothClass.Device.Major.WEARABLE -> "ウェアラブル"
            android.bluetooth.BluetoothClass.Device.Major.HEALTH -> "健康機器"
            android.bluetooth.BluetoothClass.Device.Major.IMAGING -> "画像機器"
            android.bluetooth.BluetoothClass.Device.Major.NETWORKING -> "ネットワーク機器"
            else -> "その他"
        }
    } catch (e: Exception) { "その他" }
}
