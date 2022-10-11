package com.forceai.android.app

import com.forceai.android.aoplization.annotation.ProxyEntry
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


fun main(): Unit = runBlocking {
  coroutineFunction("")
  coroutineFunction2("")
}

@ProxyEntry
suspend fun coroutineFunction(param: String): Int? {
  return 5
}

@ProxyEntry
suspend fun coroutineFunction2(param: String) = suspendCoroutine<Int?> {
  thread {
    it.resume(5)
  }
}