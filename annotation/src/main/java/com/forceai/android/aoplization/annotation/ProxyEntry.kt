package com.forceai.android.aoplization.annotation

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@ProxyUse
annotation class ProxyEntry(
  val handlerKey: String = ""
)