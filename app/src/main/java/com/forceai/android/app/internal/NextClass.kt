package com.forceai.android.app.internal

import com.forceai.android.aoplization.annotation.ProxyEntry
import com.forceai.android.app.Handler3

class NextClass {

  @ProxyEntry()
  fun abc(): Any? {
    return null
  }

  companion object {

    @ProxyEntry(Handler3::class)
    private fun bbbbb(ccc: Class<*>): Any? {
      return null
    }

  }

}