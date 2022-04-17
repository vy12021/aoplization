package com.forceai.android.aoplization

import androidx.annotation.Keep

@Keep
abstract class ProxyHandler {

  abstract fun invoke(context: ProxyContext, continuation: ProxyContinuation): Any?

}