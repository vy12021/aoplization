package com.forceai.android.aoplization.annotation

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@ProxyUse
annotation class ProxyHostMethodMeta(
  // name(java.lang.String,java.lang.Integer)
  val sign: String,
)