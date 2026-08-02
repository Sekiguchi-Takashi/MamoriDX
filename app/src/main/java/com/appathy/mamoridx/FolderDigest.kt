package com.appathy.mamoridx

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject

/**
 * フォルダ内のファイル数をダイジェスト集計し、日付つきで記録する。
 * 前回との差分で「知らないうちに増えた」ことに気付けるようにする。
 */
object FolderDigest {

    private const val PREF = "mamoridx_digest"
    private const val K_TARGETS = "targets"
    private const val K_HISTORY = "history"
    private const val MAX_HISTORY_PER_TARGET = 30

    /** 注意すべき拡張子 */
    private val riskyExt = mapOf(
        "exe" to "実行ファイル", "scr" to "実行ファイル", "com" to "実行ファイル",
        "pif" to "実行ファイル", "msi" to "インストーラ", "msp" to "インストーラ",
        "bat" to "バッチ", "cmd" to "バッチ", "ps1" to "スクリプト",
        "vbs" to "スクリプト", "vbe" to "スクリプト", "js" to "スクリプト",
        "jse" to "スクリプト", "wsf" to "スクリプト", "wsh" to "スクリプト",
        "hta" to "スクリプト", "jar" to "Javaプログラム", "apk" to "Androidアプリ",
        "sh" to "シェルスクリプト", "reg" to "レジストリ変更",
        "dll" to "プログラム部品", "cpl" to "プログラム部品", "scf" to "自動実行",
        "lnk" to "ショートカット", "url" to "ショートカット",
        "docm" to "マクロ付き文書", "xlsm" to "マクロ付き表計算",
        "pptm" to "マクロ付き資料", "xlsb" to "マクロ付き表計算",
        "dotm" to "マクロ付き雛形", "xltm" to "マクロ付き雛形"
    )

    private val docExt = setOf("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","csv","rtf","odt")
    private val imgExt = setOf("jpg","jpeg","png","gif","bmp","webp","heic","heif","tif","tiff")
    private val movExt = setOf("mp4","mov","avi","mkv","wmv","flv","m4v","3gp")
    private val audExt = setOf("mp3","wav","aac","flac","m4a","ogg")
    private val zipExt = setOf("zip","rar","7z","tar","gz","bz2","lzh","cab")

    data class Target(val id: String, var name: String, var uri: String)

    data class RiskyFile(val name: String, val path: String, val reason: String)

    data class Snapshot(
        val time: Long,
        val totalFiles: Int,
        val totalDirs: Int,
        val byCategory: Map<String, Int>,
        val riskyCount: Int,
        val truncated: Boolean,
        val riskyFiles: List<RiskyFile>
    )

    data class Diff(
        val prevTime: Long,
        val fileDelta: Int,
        val riskyDelta: Int,
        val categoryDelta: Map<String, Int>
    )

    // ---------------- 対象フォルダ ----------------
    fun targets(ctx: Context): MutableList<Target> {
        val out = mutableListOf<Target>()
        try {
            val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(K_TARGETS, "[]") ?: "[]"
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Target(o.optString("id"), o.optString("n"), o.optString("u")))
            }
        } catch (e: Exception) { }
        return out
    }

    private fun saveTargets(ctx: Context, list: List<Target>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("n", it.name); put("u", it.uri)
            })
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(K_TARGETS, arr.toString()).apply()
    }

    fun addTarget(ctx: Context, name: String, uri: String): Target {
        val list = targets(ctx)
        val t = Target(System.currentTimeMillis().toString(), name, uri)
        list.add(t)
        saveTargets(ctx, list)
        return t
    }

    fun removeTarget(ctx: Context, id: String) {
        saveTargets(ctx, targets(ctx).filter { it.id != id })
        val all = historyAll(ctx).toMutableMap()
        all.remove(id)
        saveHistoryAll(ctx, all)
    }

    // ---------------- 履歴 ----------------
    private fun historyAll(ctx: Context): Map<String, List<Snapshot>> {
        val out = HashMap<String, List<Snapshot>>()
        try {
            val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(K_HISTORY, "{}") ?: "{}"
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = root.getJSONArray(k)
                val snaps = mutableListOf<Snapshot>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val cat = HashMap<String, Int>()
                    val co = o.optJSONObject("cat")
                    if (co != null) {
                        val ck = co.keys()
                        while (ck.hasNext()) { val c = ck.next(); cat[c] = co.getInt(c) }
                    }
                    snaps.add(Snapshot(
                        o.optLong("t"), o.optInt("f"), o.optInt("d"),
                        cat, o.optInt("r"), o.optBoolean("tr", false), emptyList()))
                }
                out[k] = snaps
            }
        } catch (e: Exception) { }
        return out
    }

    private fun saveHistoryAll(ctx: Context, map: Map<String, List<Snapshot>>) {
        val root = JSONObject()
        for ((k, snaps) in map) {
            val arr = JSONArray()
            snaps.takeLast(MAX_HISTORY_PER_TARGET).forEach { s ->
                arr.put(JSONObject().apply {
                    put("t", s.time); put("f", s.totalFiles); put("d", s.totalDirs)
                    put("r", s.riskyCount); put("tr", s.truncated)
                    put("cat", JSONObject().apply {
                        for ((c, n) in s.byCategory) put(c, n)
                    })
                })
            }
            root.put(k, arr)
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(K_HISTORY, root.toString()).apply()
    }

    fun history(ctx: Context, targetId: String): List<Snapshot> =
        historyAll(ctx)[targetId] ?: emptyList()

    fun record(ctx: Context, targetId: String, snap: Snapshot) {
        val all = historyAll(ctx).toMutableMap()
        val list = (all[targetId] ?: emptyList()).toMutableList()
        list.add(snap)
        all[targetId] = list.takeLast(MAX_HISTORY_PER_TARGET)
        saveHistoryAll(ctx, all)
    }

    fun clearHistory(ctx: Context, targetId: String) {
        val all = historyAll(ctx).toMutableMap()
        all.remove(targetId)
        saveHistoryAll(ctx, all)
    }

    fun diff(prev: Snapshot?, cur: Snapshot): Diff? {
        if (prev == null) return null
        val cats = (prev.byCategory.keys + cur.byCategory.keys)
        val d = HashMap<String, Int>()
        for (c in cats) {
            val delta = (cur.byCategory[c] ?: 0) - (prev.byCategory[c] ?: 0)
            if (delta != 0) d[c] = delta
        }
        return Diff(prev.time,
            cur.totalFiles - prev.totalFiles,
            cur.riskyCount - prev.riskyCount, d)
    }

    // ---------------- 集計 ----------------
    fun scan(ctx: Context, treeUri: Uri, maxFiles: Int = 4000): Snapshot {
        val counts = LinkedHashMap<String, Int>()
        val risky = mutableListOf<RiskyFile>()
        var files = 0
        var dirs = 0
        var truncated = false

        fun bump(cat: String) { counts[cat] = (counts[cat] ?: 0) + 1 }

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        val queue = ArrayDeque<Pair<String, String>>()
        try {
            queue.add(DocumentsContract.getTreeDocumentId(treeUri) to "")
        } catch (e: Exception) {
            return Snapshot(System.currentTimeMillis(), 0, 0, emptyMap(), 0, false, emptyList())
        }

        var guard = 0
        loop@ while (queue.isNotEmpty() && guard < 8000) {
            guard++
            val (docId, parent) = queue.removeFirst()
            val childUri = try {
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            } catch (e: Exception) { continue }
            val cursor = try {
                ctx.contentResolver.query(childUri, projection, null, null, null)
            } catch (e: Exception) { null } ?: continue

            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2) ?: ""
                    val path = if (parent.isEmpty()) name else "$parent/$name"

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        dirs++
                        queue.add(id to path)
                        continue
                    }
                    files++
                    if (files > maxFiles) { truncated = true; return@use }

                    val lower = name.lowercase()
                    val ext = lower.substringAfterLast('.', "")

                    val riskLabel = riskyExt[ext]
                    val parts = lower.split('.')
                    val doubleExt = parts.size >= 3 &&
                        (docExt.contains(parts[parts.size - 2]) ||
                         imgExt.contains(parts[parts.size - 2])) &&
                        riskyExt.containsKey(ext)

                    when {
                        doubleExt -> {
                            bump("危険")
                            risky.add(RiskyFile(name, path,
                                "二重拡張子（.${parts[parts.size-2]}.$ext）。" +
                                "文書や画像に偽装した実行ファイルの典型です"))
                        }
                        riskLabel != null -> {
                            bump("危険")
                            risky.add(RiskyFile(name, path, "$riskLabel（.$ext）"))
                        }
                        docExt.contains(ext) -> bump("文書")
                        imgExt.contains(ext) -> bump("画像")
                        movExt.contains(ext) -> bump("動画")
                        audExt.contains(ext) -> bump("音声")
                        zipExt.contains(ext) -> bump("圧縮")
                        ext.isEmpty() -> bump("拡張子なし")
                        else -> bump("その他")
                    }
                }
            }
            if (files > maxFiles) break@loop
        }
        if (queue.isNotEmpty()) truncated = true

        return Snapshot(
            System.currentTimeMillis(), files, dirs, counts,
            risky.size, truncated, risky.sortedBy { it.name })
    }
}
