package com.forceai.android.aoplization

import androidx.annotation.Keep
import com.forceai.android.aoplization.annotation.MainProxyHandler

@Keep
interface ProxyHandler {

  fun invoke(context: ProxyContext, continuation: ProxyContinuation): Any?

}

@MainProxyHandler
class DefaultHandler: ProxyHandler {
  override fun invoke(context: ProxyContext, continuation: ProxyContinuation): Any? {
    return null
  }
}