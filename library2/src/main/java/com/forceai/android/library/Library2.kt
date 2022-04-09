package com.forceai.android.library

import android.content.Context
import android.widget.Toast

object Library2 {

  @JvmStatic
  fun invoke(context: Context) {
    Toast.makeText(context, "调用library2", Toast.LENGTH_SHORT).show()
  }

}