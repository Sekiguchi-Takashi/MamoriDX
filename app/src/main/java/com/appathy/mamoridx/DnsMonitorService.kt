package com.appathy.mamoridx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * A案: DNS問い合わせの「記録専用」VpnService。
 * 端末の通信を実際には転送せず、DNS(ポート53)のクエリ名だけを抽出してログ化する。
 * VPN有効中は通信が流れない「棚卸しモード」である点に注意（説明書タブに明記）。
 */
class DnsMonitorService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private var worker: Thread? = null

    companion object {
        const val ACTION_START = "com.appathy.mamoridx.START"
        const val ACTION_STOP = "com.appathy.mamoridx.STOP"
        private const val CHANNEL_ID = "mamoridx_vpn"
        private const val NOTIF_ID = 1

        @Volatile var isRunning = false
            private set
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitor()
                return START_NOT_STICKY
            }
            else -> startMonitor()
        }
        return START_STICKY
    }

    private fun startMonitor() {
        if (running) return
        DnsLogStore.load(applicationContext)

        val builder = Builder()
            .setSession("守りのDX 通信記録")
            .addAddress("10.111.222.1", 32)
            // DNSクエリだけをVPNに引き込む（全通信は取り込まない=軽量）
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .setBlocking(true)
        // 自分自身は除外（ログの自己参照を避ける）
        try { builder.addDisallowedApplication(packageName) } catch (e: Exception) { }

        vpnInterface = try {
            builder.establish()
        } catch (e: Exception) {
            null
        }
        if (vpnInterface == null) {
            stopSelf()
            return
        }

        running = true
        isRunning = true
        startForeground(NOTIF_ID, buildNotification())

        worker = Thread { loop() }.also { it.start() }
    }

    private fun loop() {
        val fd = vpnInterface ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val packet = ByteArray(32767)
        while (running) {
            val len = try { input.read(packet) } catch (e: Exception) { -1 }
            if (len <= 0) {
                // 記録専用モードでは書き戻さないため、read待ちが続く。中断で抜ける
                if (!running) break
                continue
            }
            try {
                parseDns(packet, len)
            } catch (e: Exception) { }
            // A案: パケットは転送も書き戻しもしない（ドロップ）
        }
    }

    /**
     * IPv4 + UDP(53) のパケットからDNS QNAMEを抽出する。
     * IPv6やTCP、非53ポートは対象外（安全に無視）。
     */
    private fun parseDns(data: ByteArray, len: Int) {
        if (len < 28) return
        val buf = ByteBuffer.wrap(data, 0, len)

        val versionIhl = buf.get(0).toInt() and 0xFF
        val version = versionIhl shr 4
        if (version != 4) return
        val ihl = (versionIhl and 0x0F) * 4
        if (ihl < 20 || ihl > len) return

        val protocol = buf.get(9).toInt() and 0xFF
        if (protocol != 17) return // UDP以外は無視

        val udpStart = ihl
        if (udpStart + 8 > len) return
        val destPort = ((buf.get(udpStart + 2).toInt() and 0xFF) shl 8) or
            (buf.get(udpStart + 3).toInt() and 0xFF)
        if (destPort != 53) return

        val dnsStart = udpStart + 8
        // DNSヘッダ12バイト。QDCOUNTが1以上のクエリのみ扱う
        if (dnsStart + 12 > len) return
        val qdCount = ((buf.get(dnsStart + 4).toInt() and 0xFF) shl 8) or
            (buf.get(dnsStart + 5).toInt() and 0xFF)
        if (qdCount < 1) return

        // QNAME をラベル長プレフィックス方式でパース
        var pos = dnsStart + 12
        val sb = StringBuilder()
        var guard = 0
        while (pos < len && guard < 128) {
            val labelLen = buf.get(pos).toInt() and 0xFF
            if (labelLen == 0) break
            if (labelLen and 0xC0 != 0) return // 圧縮ポインタはクエリ先頭では通常来ない
            pos++
            if (pos + labelLen > len) return
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until labelLen) {
                val c = buf.get(pos + i).toInt() and 0xFF
                sb.append(c.toChar())
            }
            pos += labelLen
            guard++
        }

        val domain = sb.toString().lowercase().trim()
        if (domain.isNotEmpty() && domain.contains('.')) {
            DnsLogStore.record(applicationContext, domain)
        }
    }

    private fun stopMonitor() {
        running = false
        isRunning = false
        try { worker?.interrupt() } catch (e: Exception) { }
        try { vpnInterface?.close() } catch (e: Exception) { }
        vpnInterface = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopMonitor()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopMonitor()
        super.onRevoke()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL_ID, "通信記録",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= 23)
                PendingIntent.FLAG_IMMUTABLE else 0
        )
        val b = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, CHANNEL_ID) else
            @Suppress("DEPRECATION") Notification.Builder(this)
        return b
            .setContentTitle("守りのDX：通信記録中")
            .setContentText("DNS問い合わせを記録しています（記録専用モード）")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
