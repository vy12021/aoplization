package com.forceai.android.aoplization.annotation

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@ProxyUse
annotation class ProxyHostMethodMeta(
  // click2LikeItem
  val name: String,
  // [com.forceai.android.app.Item]
  val params: Array<String>
)