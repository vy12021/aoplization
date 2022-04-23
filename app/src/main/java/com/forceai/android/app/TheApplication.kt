package com.forceai.android.app

import com.forceai.android.aoplization.ProxyContext
import com.forceai.android.aoplization.ProxyContinuation
import com.forceai.android.aoplization.ProxyHandler
import com.forceai.android.aoplization.annotation.MainProxyHandler
import com.forceai.android.aoplization.annotation.ProxyEntry

class ClassInFile {

  companion object {

    //@JvmStatic
    @ProxyEntry("2")
    private fun companionFunction(string: String): Any? = null

  }

  @ProxyEntry("2")
  private fun memberFunction(arg: Any): Any? = ObjectClass.objectFunction(arg)

  class AInClass {

    companion object {

      @ProxyEntry("2")
      private fun innerCompanionFunc(): Any? = null

    }

    @ProxyEntry("2")
    private fun innerMemberFunc(): Any? = null

    object BObjectInA {

      @ProxyEntry("2")
      private fun func(): Any? = null

      class CInB {

        @ProxyEntry("2")
        private fun func(): Any? = null

      }

    }

  }

}

object ObjectClass {

  private val aaa: String = "222"

  @ProxyEntry("2")
  fun objectFunction(arg: Any): Any? = aaa

}

@ProxyEntry("2")
private fun topLevelFunction(arg: Any): Any? = null

@MainProxyHandler("2")
class DefaultHandler: ProxyHandler() {
  override fun invoke(context: ProxyContext, continuation: ProxyContinuation): Any? {
    return when (context.annotations.firstOrNull()) {
      else -> null
    }
  }
}