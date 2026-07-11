package com.hupux.data

import kotlinx.datetime.Clock

/** KMP 版当前毫秒时间戳（替代 JVM 的 System.currentTimeMillis()）。 */
internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
