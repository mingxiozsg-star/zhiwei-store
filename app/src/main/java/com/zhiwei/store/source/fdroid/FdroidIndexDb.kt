package com.zhiwei.store.source.fdroid

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 把 F-Droid 全量索引落到本地 SQLite，搜索走 SQL 而不是每次都拉网络。
 */
class FdroidIndexDb(context: Context, dbName: String = "fdroid_index.db") {

    private val helper = object : SQLiteOpenHelper(context.applicationContext, dbName, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(CREATE)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_apps_name ON apps(name)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_apps_updated ON apps(updated)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_apps_added ON apps(added)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS apps")
            onCreate(db)
        }
    }

    fun count(): Int {
        return runCatching {
            helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM apps", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        }.getOrDefault(0)
    }

    /** 用新索引整体替换旧数据：先灌新表，成功后原子切换，解析失败保留旧数据 */
    fun replaceAll(entries: List<FdroidIndexParser.Entry>): Int {
        val db = helper.writableDatabase
        db.execSQL("DROP TABLE IF EXISTS apps_new")
        db.execSQL(CREATE.replace("apps", "apps_new"))
        db.beginTransaction()
        var written = 0
        try {
            entries.forEach { e ->
                val cv = ContentValues().apply {
                    put("pkg", e.packageName)
                    put("name", e.displayName())
                    put("summary", e.displaySummary())
                    put("description", e.description.take(20000))
                    put("icon", e.icon)
                    put("vername", e.versionName)
                    put("vercode", e.versionCode)
                    put("apk", e.apkName)
                    put("hash", e.hash)
                    put("size", e.size)
                    put("updated", e.lastUpdated)
                    put("added", e.added)
                    put("license", e.license)
                    put("website", e.website)
                    put("sourcecode", e.sourceCode)
                    put("author", e.authorName)
                    put("categories", e.categories.joinToString("|"))
                    put("antiflags", e.antiFeatures.joinToString("|"))
                }
                if (db.insertWithOnConflict("apps_new", null, cv, SQLiteDatabase.CONFLICT_REPLACE) >= 0) {
                    written++
                }
            }
            db.execSQL("DROP TABLE IF EXISTS apps")
            db.execSQL("ALTER TABLE apps_new RENAME TO apps")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_apps_name ON apps(name)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_apps_updated ON apps(updated)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_apps_added ON apps(added)")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return written
    }

    fun search(query: String, limit: Int = 60): List<FdroidIndexParser.Entry> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val like = "%$q%"
        return queryList(
            "SELECT * FROM apps WHERE name LIKE ? OR pkg LIKE ? OR summary LIKE ? " +
                "ORDER BY CASE WHEN pkg = ? THEN 0 WHEN name = ? THEN 1 ELSE 2 END, updated DESC LIMIT ?",
            arrayOf(like, like, like, q, q, limit.toString())
        )
    }

    fun recentUpdated(limit: Int = 30): List<FdroidIndexParser.Entry> =
        queryList("SELECT * FROM apps ORDER BY updated DESC LIMIT ?", arrayOf(limit.toString()))

    fun newest(limit: Int = 30): List<FdroidIndexParser.Entry> =
        queryList("SELECT * FROM apps ORDER BY added DESC LIMIT ?", arrayOf(limit.toString()))

    fun byPackage(pkg: String): FdroidIndexParser.Entry? =
        queryList("SELECT * FROM apps WHERE pkg = ? LIMIT 1", arrayOf(pkg)).firstOrNull()

    fun categories(): List<String> {
        val out = LinkedHashSet<String>()
        runCatching {
            helper.readableDatabase.rawQuery("SELECT categories FROM apps WHERE categories != '' LIMIT 2000", null)
                .use { c ->
                    while (c.moveToNext()) {
                        c.getString(0)?.split("|")?.forEach { if (it.isNotBlank()) out.add(it) }
                    }
                }
        }
        return out.toList()
    }

    private fun queryList(sql: String, args: Array<String>): List<FdroidIndexParser.Entry> {
        val list = ArrayList<FdroidIndexParser.Entry>()
        runCatching {
            helper.readableDatabase.rawQuery(sql, args).use { c ->
                while (c.moveToNext()) list.add(c.toEntry())
            }
        }
        return list
    }

    private fun Cursor.toEntry(): FdroidIndexParser.Entry {
        val e = FdroidIndexParser.Entry()
        e.packageName = getString(getColumnIndexOrThrow("pkg")) ?: ""
        e.name = getString(getColumnIndexOrThrow("name")) ?: ""
        e.summary = getString(getColumnIndexOrThrow("summary")) ?: ""
        e.description = getString(getColumnIndexOrThrow("description")) ?: ""
        e.icon = getString(getColumnIndexOrThrow("icon")) ?: ""
        e.versionName = getString(getColumnIndexOrThrow("vername")) ?: ""
        e.versionCode = getLong(getColumnIndexOrThrow("vercode"))
        e.apkName = getString(getColumnIndexOrThrow("apk")) ?: ""
        e.hash = getString(getColumnIndexOrThrow("hash")) ?: ""
        e.size = getLong(getColumnIndexOrThrow("size"))
        e.lastUpdated = getLong(getColumnIndexOrThrow("updated"))
        e.added = getLong(getColumnIndexOrThrow("added"))
        e.license = getString(getColumnIndexOrThrow("license")) ?: ""
        e.website = getString(getColumnIndexOrThrow("website")) ?: ""
        e.sourceCode = getString(getColumnIndexOrThrow("sourcecode")) ?: ""
        e.authorName = getString(getColumnIndexOrThrow("author")) ?: ""
        e.categories = (getString(getColumnIndexOrThrow("categories")) ?: "").split("|").filter { it.isNotBlank() }
        e.antiFeatures = (getString(getColumnIndexOrThrow("antiflags")) ?: "").split("|").filter { it.isNotBlank() }
        return e
    }

    companion object {
        private const val CREATE = """
            CREATE TABLE IF NOT EXISTS apps (
                pkg TEXT PRIMARY KEY,
                name TEXT,
                summary TEXT,
                description TEXT,
                icon TEXT,
                vername TEXT,
                vercode INTEGER,
                apk TEXT,
                hash TEXT,
                size INTEGER,
                updated INTEGER,
                added INTEGER,
                license TEXT,
                website TEXT,
                sourcecode TEXT,
                author TEXT,
                categories TEXT,
                antiflags TEXT
            )
        """
    }
}