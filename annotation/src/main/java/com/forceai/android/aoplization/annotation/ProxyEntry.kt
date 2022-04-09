package com.forceai.android.aoplization.annotation

import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class ProxyEntry(
  val handler: KClass<*> = Any::class
)