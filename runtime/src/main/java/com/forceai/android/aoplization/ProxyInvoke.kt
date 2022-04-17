package com.forceai.android.aoplization

import java.lang.ref.WeakReference
import java.util.concurrent.Callable

/**
 * 关于是否有可能产生的内存泄露处理，还未相当好办法，调用点可能被外部持有造成泄露
 */
class ProxyInvoke(function: () -> Any?): Callable<Any?> {

  private val funcRef = WeakReference(function)

  override fun call(): Any? {
    return funcRef.get()?.invoke()
  }

}