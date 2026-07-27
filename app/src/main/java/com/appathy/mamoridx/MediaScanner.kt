package com.appathy.mamoridx

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Phase 5: 外部メディア（USBメモリ/SDカード）のファイル検査。
 * SAF(ストレージアクセスフレームワーク)でユーザーが選んだフォルダのみを走査する。
 * ウイルス定義は持たないため「危険な形式・偽装の指摘」までを行う。
 */
object MediaScanner {

    const val SEV_INFO = 0
    const val SEV_WARN = 1
    const val SEV_DANGER = 2

    data class Item(
        val name: String,
        val path: String,
        val severity: Int,
        val reasons: List<String>
    )

    data class Report(
        val totalFiles: Int,
        val totalDirs: Int,
        val truncated: Boolean,
        val items: List<Item>
    )

    /** 実行形式・スクリプト系（Windows/Android両方） */
    private val execExt = setOf(
        "exe", "scr", "com", "pif", "bat", "cmd", "msi", "msp", "dll", "cpl",
        "vbs", "vbe", "js", "jse", "wsf", "wsh", "hta", "ps1", "psm1", "reg",
        "jar", "apk", "app", "sh", "run", "bin", "gadget", "inf", "scf"
    )

    /** ショートカット・リンク系（実体を偽装しやすい） */
    private val linkExt = setOf("lnk", "url", "desktop", "website")

    /** マクロを含められるOffice形式 */
    private val macroExt = setOf("docm", "xlsm", "pptm", "xlsb", "dotm", "xltm", "potm", "ppsm")

    /** 一見安全に見える拡張子（二重拡張子の前半に使われる） */
    private val innocentExt = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf",
        "jpg", "jpeg", "png", "gif", "bmp", "mp3", "mp4", "avi", "mov", "zip", "html"
    )

    /** 書字方向制御文字（ファイル名偽装の常套手段） */
    private val rtlChars = charArrayOf(
        '\u202A', '\u202B', '\u202C', '\u202D', '\u202E', '\u200E', '\u200F', '\u061C'
    )

    fun scan(ctx: Context, treeUri: Uri, maxFiles: Int = 1500): Report {
        val items = mutableListOf<Item>()
        var files = 0
        var dirs = 0
        var truncated = false

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        val queue = ArrayDeque<Pair<String, String>>()
        try {
            queue.add(DocumentsContract.getTreeDocumentId(treeUri) to "")
        } catch (e: Exception) {
            return Report(0, 0, false, emptyList())
        }

        var depthGuard = 0
        while (queue.isNotEmpty() && files < maxFiles && depthGuard < 5000) {
            depthGuard++
            val (docId, parentPath) = queue.removeFirst()
            val childrenUri = try {
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            } catch (e: Exception) { continue }

            val cursor = try {
                ctx.contentResolver.query(childrenUri, projection, null, null, null)
            } catch (e: Exception) { null } ?: continue

            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2) ?: ""
                    val size = try { c.getLong(3) } catch (e: Exception) { 0L }
                    val path = if (parentPath.isEmpty()) name else "$parentPath/$name"

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        dirs++
                        queue.add(id to path)
                        continue
                    }

                    files++
                    if (files > maxFiles) { truncated = true; return@use }

                    val reasons = mutableListOf<String>()
                    var sev = SEV_INFO
                    fun flag(s: Int, msg: String) {
                        reasons.add(msg)
                        if (s > sev) sev = s
                    }

                    val lower = name.lowercase()
                    val ext = lower.substringAfterLast('.', "")

                    // 1. 書字方向制御文字による偽装
                    if (name.any { ch -> rtlChars.contains(ch) }) {
                        flag(SEV_DANGER,
                            "ファイル名に文字の並びを逆転させる特殊文字が含まれます。" +
                            "「請求書fdp.exe」を「請求書exe.pdf」のように見せる偽装手口です")
                    }

                    // 2. 二重拡張子
                    val parts = lower.split('.')
                    if (parts.size >= 3) {
                        val prev = parts[parts.size - 2]
                        if (innocentExt.contains(prev) &&
                            (execExt.contains(ext) || linkExt.contains(ext))) {
                            flag(SEV_DANGER,
                                "二重拡張子です（.$prev.$ext）。文書や画像に偽装した実行ファイルの典型です")
                        }
                    }

                    // 3. 実行形式
                    if (execExt.contains(ext)) {
                        flag(SEV_WARN,
                            "実行形式またはスクリプトです（.$ext）。開くとプログラムが動作します")
                    }

                    // 4. ショートカット
                    if (linkExt.contains(ext)) {
                        flag(SEV_WARN,
                            "ショートカット（.$ext）です。リンク先に不正なコマンドを仕込めます")
                    }

                    // 5. マクロ付きOffice
                    if (macroExt.contains(ext)) {
                        flag(SEV_WARN,
                            "マクロを含められる形式（.$ext）です。「コンテンツの有効化」を押さないでください")
                    }

                    // 6. autorun
                    if (lower == "autorun.inf") {
                        flag(SEV_DANGER,
                            "自動実行設定ファイルです。接続しただけで別のプログラムを起動させる目的で使われます")
                    }

                    // 7. 極端に長い名前
                    if (name.length > 80) {
                        flag(SEV_WARN,
                            "ファイル名が異常に長い（${name.length}文字）。末尾の拡張子を隠す狙いがあります")
                    }

                    // 8. 空白を大量に含む名前
                    if (Regex("\\s{6,}").containsMatchIn(name)) {
                        flag(SEV_WARN, "ファイル名に大量の空白が含まれます。拡張子の隠蔽が疑われます")
                    }

                    // 9. 中身と拡張子の不一致（マジックバイト照合）
                    val magic = readMagic(ctx, treeUri, id)
                    if (magic != null) {
                        val actual = detectType(magic)
                        if (actual == "windows_exe" &&
                            !execExt.contains(ext)) {
                            flag(SEV_DANGER,
                                "拡張子は.${ext.ifEmpty { "なし" }}ですが、中身はWindows実行ファイルです。完全な偽装です")
                        }
                        if (actual == "elf" && ext !in setOf("so", "bin", "elf", "o")) {
                            flag(SEV_DANGER,
                                "拡張子は.${ext.ifEmpty { "なし" }}ですが、中身は実行プログラムです")
                        }
                        if (ext == "pdf" && actual != "pdf" && actual != "unknown") {
                            flag(SEV_WARN, "拡張子は.pdfですが、中身がPDFではありません")
                        }
                        if ((ext == "jpg" || ext == "jpeg") && actual != "jpeg" && actual != "unknown") {
                            flag(SEV_WARN, "拡張子は.$ext ですが、中身が画像ではありません")
                        }
                    }

                    // 10. サイズ0
                    if (size == 0L && ext.isNotEmpty()) {
                        flag(SEV_INFO, "ファイルサイズが0です（囮または破損の可能性）")
                    }

                    if (reasons.isNotEmpty() && sev > SEV_INFO) {
                        items.add(Item(name, path, sev, reasons))
                    }
                }
            }
        }
        if (queue.isNotEmpty()) truncated = true

        return Report(files, dirs, truncated,
            items.sortedByDescending { it.severity })
    }

    private fun readMagic(ctx: Context, treeUri: Uri, docId: String): ByteArray? = try {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        ctx.contentResolver.openInputStream(uri)?.use { s ->
            val b = ByteArray(8)
            val n = s.read(b)
            if (n <= 0) null else b
        }
    } catch (e: Exception) { null }

    private fun detectType(b: ByteArray): String {
        if (b.size < 4) return "unknown"
        val u = b.map { it.toInt() and 0xFF }
        return when {
            u[0] == 0x4D && u[1] == 0x5A -> "windows_exe"                    // MZ
            u[0] == 0x7F && u[1] == 0x45 && u[2] == 0x4C && u[3] == 0x46 -> "elf"
            u[0] == 0x25 && u[1] == 0x50 && u[2] == 0x44 && u[3] == 0x46 -> "pdf"
            u[0] == 0xFF && u[1] == 0xD8 && u[2] == 0xFF -> "jpeg"
            u[0] == 0x89 && u[1] == 0x50 && u[2] == 0x4E && u[3] == 0x47 -> "png"
            u[0] == 0x50 && u[1] == 0x4B -> "zip"                            // PK
            u[0] == 0x47 && u[1] == 0x49 && u[2] == 0x46 -> "gif"
            u[0] == 0xD0 && u[1] == 0xCF -> "ole"                            // 旧Office
            else -> "unknown"
        }
    }
}
