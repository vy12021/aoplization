package com.forceai.android.aoplization.annotation

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@ProxyUse
annotation class ProxyHostMeta(
  // com.forceai.android.app.MainActivity
  val clazz: String
)