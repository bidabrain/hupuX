package com.hupux.data

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 用于把阻塞/IO 工作从主线程挪开的调度器。
 * JVM（Android/Desktop）用 Dispatchers.IO；Native（iOS）没有 IO，用 Dispatchers.Default。
 * 注：Ktor/SQLDelight 本身已在自有线程上异步执行，这里主要是统一 offload 入口。
 */
internal expect val ioDispatcher: CoroutineDispatcher
