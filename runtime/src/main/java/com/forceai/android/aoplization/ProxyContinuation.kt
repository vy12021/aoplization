package com.forceai.android.aoplization

interface ProxyContinuation {
  fun resume(returnValue: Any?): Any?
}