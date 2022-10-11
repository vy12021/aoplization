package com.forceai.android.app

import com.forceai.android.aoplization.ProxyContext
import com.forceai.android.aoplization.ProxyContinuation
import com.forceai.android.aoplization.ProxyHandler
import com.forceai.android.aoplization.annotation.MainProxyHandler
import com.forceai.android.aoplization.annotation.ProxyEntry

class ClassInFile {

  companion object {

    @JvmStatic
    @ProxyEntry()
    private fun companionFunction(string: String): Any? = null

  }

  @ProxyEntry()
  private fun memberFunction(arg: Any): Any? = ObjectClass.objectFunction(arg)

  class AInClass {

    companion object {

      @ProxyEntry()
      private fun innerCompanionFunc(): Any? = null

    }

    @ProxyEntry()
    private fun innerMemberFunc(): Any? = null

    object BObjectInA {

      @ProxyEntry()
      private fun func(): Any? = null

      class CInB {

        @ProxyEntry()
        private fun func(): Any? = null

      }

    }

  }

}

object ObjectClass {

  private val aaa: String = "222"

  @ProxyEntry()
  fun objectFunction(arg: Any): Any? = aaa

}

@ProxyEntry
private fun topLevelFunction(arg: Any): String? = null

@MainProxyHandler
class DefaultHandler: ProxyHandler {
  override fun invoke(context: ProxyContext, continuation: ProxyContinuation): Any? {
    return when (context.annotations.firstOrNull()) {
      else -> null
    }
  }
}