package com.hupux.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * 兼容从旧版本升级上来的用户。
 *
 * v1.1 及以前用 Room，数据库文件同样叫 `favorites.db`，且 `@Database(version = 2)`。
 * v1.2.0 起改用 SQLDelight（schema 版本 1），复用了同名文件。于是老用户升级后，
 * SQLite 发现现有库版本(2) > 当前(1)，默认 onDowngrade 直接抛异常 → 启动闪退。
 *
 * 这里在版本不兼容（降级）时清空所有旧表并按当前 SQLDelight schema 重建，避免崩溃。
 * 正常的 SQLDelight 用户版本一致，不会触发此回调，数据不受影响；
 * 只有残留 Room 旧库的用户会被重建一次（旧的本地收藏/关注会丢失，可重新添加）。
 */
class ResilientSqliteCallback(
    private val schema: SqlSchema<QueryResult.Value<Unit>>
) : AndroidSqliteDriver.Callback(schema) {

    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
        recreate(db)
    }

    private fun recreate(db: SupportSQLiteDatabase) {
        val tables = mutableListOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'"
        ).use { cursor ->
            while (cursor.moveToNext()) tables.add(cursor.getString(0))
        }
        tables.forEach { db.execSQL("DROP TABLE IF EXISTS `$it`") }
        onCreate(db)   // 重新按 SQLDelight schema 建表
    }
}
