package com.forceai.android.app

import com.forceai.android.aoplization.ProxyContext
import com.forceai.android.aoplization.ProxyContinuation
import com.forceai.android.aoplization.ProxyHandler
import com.forceai.android.aoplization.annotation.MainProxyHandler

@MainProxyHandler
class Handler3: ProxyHandler {
  override fun invoke(context: ProxyContext, continuation: ProxyContinuation): Any? {
    return continuation.resume(null)
  }
}